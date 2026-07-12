package com.kbpack.parser;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.task.ParseTask;
import com.kbpack.task.ParseTaskRepository;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ContentController {
    private static final List<String> SAFE_INLINE_IMAGE_TYPES = List.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif"
    );
    private final PackageVersionRepository versionRepository;
    private final KnowledgePackageRepository packageRepository;
    private final PackageAssetRepository assetRepository;
    private final ExtractedDocumentRepository documentRepository;
    private final ParseTaskRepository taskRepository;
    private final ObjectStorageService storage;
    private final AuthService authService;

    public ContentController(
            PackageVersionRepository versionRepository,
            KnowledgePackageRepository packageRepository,
            PackageAssetRepository assetRepository,
            ExtractedDocumentRepository documentRepository,
            ParseTaskRepository taskRepository,
            ObjectStorageService storage,
            AuthService authService
    ) {
        this.versionRepository = versionRepository;
        this.packageRepository = packageRepository;
        this.assetRepository = assetRepository;
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.storage = storage;
        this.authService = authService;
    }

    public record ReparseRequest(String entry_file) {}

    @PostMapping("/versions/{versionId}/reparse")
    @Transactional
    public ResponseEntity<Map<String, String>> reparse(
            @PathVariable String versionId,
            @RequestBody(required = false) ReparseRequest request,
            Authentication authentication
    ) {
        AppUser user = currentUser(authentication);
        PackageVersion version = requireVersion(versionId, user);
        requireWritable(version.getPackageId(), user);
        if (taskRepository.existsByVersionIdAndStatusIn(version.getId(), List.of(
                ParseTask.Status.pending, ParseTask.Status.processing, ParseTask.Status.retry_scheduled))) {
            throw new ApiException(ErrorCode.CONFLICT, "该版本已有解析任务在执行或等待执行");
        }
        if (request != null && request.entry_file() != null && !request.entry_file().isBlank()) {
            String entry = com.kbpack.pkg.UploadService.normalizeEntryPath(request.entry_file(), 512);
            if (assetRepository.findByVersionIdAndPath(version.getId(), entry).isEmpty()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "指定入口文件不存在");
            }
            version.setEntryFile(entry);
        }
        version.setParseStatus(PackageVersion.ParseStatus.pending);
        version.setParseError(null);
        versionRepository.save(version);
        ParseTask task = new ParseTask();
        task.setVersionId(version.getId());
        task.setTaskType(ParseTask.TaskType.reparse);
        taskRepository.save(task);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("parse_status", "pending"));
    }

    @GetMapping("/versions/{versionId}/files")
    public Map<String, Object> files(@PathVariable String versionId, Authentication authentication) {
        PackageVersion version = requireVersion(versionId, currentUser(authentication));
        return Map.of("tree", buildTree(assetRepository.findByVersionIdOrderByPathAsc(version.getId())));
    }

    @GetMapping("/versions/{versionId}/files/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable String versionId, Authentication authentication) {
        PackageVersion version = requireVersion(versionId, currentUser(authentication));
        var stream = storage.open(storage.originalBucket(), version.getStoragePath());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(version.getOriginalFilename(), StandardCharsets.UTF_8).build());
        return new ResponseEntity<>(new InputStreamResource(stream), headers, HttpStatus.OK);
    }

    @GetMapping("/versions/{versionId}/assets/{*assetPath}")
    public ResponseEntity<StreamingResponseBody> asset(
            @PathVariable String versionId,
            @PathVariable String assetPath,
            Authentication authentication
    ) {
        PackageVersion version = requireVersion(versionId, currentUser(authentication));
        String rawPath = assetPath == null ? "" : assetPath.replaceFirst("^/", "");
        final String path;
        try {
            path = com.kbpack.pkg.UploadService.normalizeEntryPath(rawPath, 512);
        } catch (ApiException error) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (path.isBlank()) throw new ApiException(ErrorCode.NOT_FOUND);

        ExtractedDocument linkedDocument = documentRepository
                .findByVersionIdAndSourcePath(version.getId(), path).orElse(null);
        if (linkedDocument != null) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create("/documents/" + IdPrefix.DOCUMENT.format(linkedDocument.getId())));
            headers.setCacheControl(CacheControl.noStore());
            return new ResponseEntity<>(null, headers, HttpStatus.FOUND);
        }

        PackageAsset asset = assetRepository.findByVersionIdAndPath(version.getId(), path)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String packageId = IdPrefix.PACKAGE.format(version.getPackageId());
        String externalVersionId = IdPrefix.VERSION.format(version.getId());
        var input = storage.open(storage.packagesBucket(),
                packageId + "/" + externalVersionId + "/files/" + path);
        StreamingResponseBody body = output -> {
            try (input) {
                input.transferTo(output);
            }
        };

        String mimeType = asset.getMimeType() == null
                ? "" : asset.getMimeType().split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
        boolean safeInlineImage = SAFE_INLINE_IMAGE_TYPES.contains(mimeType);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, safeInlineImage ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        headers.setContentLength(asset.getSize());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; sandbox");
        if (!safeInlineImage) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8).build());
        }
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @GetMapping("/versions/{versionId}/documents")
    public List<Map<String, Object>> documents(@PathVariable String versionId, Authentication authentication) {
        PackageVersion version = requireVersion(versionId, currentUser(authentication));
        return documentRepository.findByVersionIdOrderByOrderNoAsc(version.getId()).stream().map(document -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", IdPrefix.DOCUMENT.format(document.getId()));
            item.put("title", document.getTitle());
            item.put("doc_type", document.getDocType().name());
            item.put("order_no", document.getOrderNo());
            item.put("word_count", document.getWordCount());
            return item;
        }).toList();
    }

    @GetMapping("/documents/{documentId}")
    public Map<String, Object> document(@PathVariable String documentId, Authentication authentication) {
        ExtractedDocument document = documentRepository.findById(parseId(documentId, IdPrefix.DOCUMENT, ErrorCode.NOT_FOUND))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "文档不存在"));
        PackageVersion version = versionRepository.findActiveById(document.getVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "文档不存在"));
        requirePackage(version.getPackageId(), currentUser(authentication));
        List<ExtractedDocument> siblings = documentRepository.findByVersionIdOrderByOrderNoAsc(version.getId());
        int index = siblings.stream().map(ExtractedDocument::getId).toList().indexOf(document.getId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", IdPrefix.DOCUMENT.format(document.getId()));
        result.put("package_id", IdPrefix.PACKAGE.format(document.getPackageId()));
        result.put("version_id", IdPrefix.VERSION.format(document.getVersionId()));
        result.put("title", document.getTitle());
        result.put("doc_type", document.getDocType().name());
        result.put("source_path", document.getSourcePath());
        result.put("content", document.getContent());
        result.put("heading_tree", document.getHeadingTree() == null ? List.of() : document.getHeadingTree());
        result.put("prev_document_id", index > 0 ? IdPrefix.DOCUMENT.format(siblings.get(index - 1).getId()) : null);
        result.put("next_document_id", index >= 0 && index + 1 < siblings.size()
                ? IdPrefix.DOCUMENT.format(siblings.get(index + 1).getId()) : null);
        return result;
    }

    private PackageVersion requireVersion(String externalId, AppUser user) {
        PackageVersion version = versionRepository.findActiveById(parseId(externalId, IdPrefix.VERSION, ErrorCode.VERSION_NOT_FOUND))
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND));
        requirePackage(version.getPackageId(), user);
        return version;
    }

    private KnowledgePackage requirePackage(UUID packageId, AppUser user) {
        KnowledgePackage pkg = packageRepository.findActiveById(packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        boolean privileged = user.getRole() == AppUser.Role.owner || user.getRole() == AppUser.Role.admin;
        if ("private".equals(pkg.getVisibility()) && !pkg.getOwnerId().equals(user.getId()) && !privileged) {
            throw new ApiException(ErrorCode.PACKAGE_NOT_FOUND);
        }
        return pkg;
    }

    private void requireWritable(UUID packageId, AppUser user) {
        KnowledgePackage pkg = requirePackage(packageId, user);
        boolean administrator = user.getRole() == AppUser.Role.owner || user.getRole() == AppUser.Role.admin;
        boolean editorOwner = user.getRole() == AppUser.Role.editor && pkg.getOwnerId().equals(user.getId());
        if (!administrator && !editorOwner) throw new ApiException(ErrorCode.FORBIDDEN);
    }

    private AppUser currentUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED);
        }
        return authService.requireUserById(authentication.getPrincipal().toString());
    }

    private UUID parseId(String value, IdPrefix prefix, ErrorCode error) {
        try { return prefix.parse(value); }
        catch (IllegalArgumentException e) { throw new ApiException(error); }
    }

    private List<Map<String, Object>> buildTree(List<PackageAsset> assets) {
        Node root = new Node("", "dir", null, null);
        for (PackageAsset asset : assets) {
            String[] parts = asset.getPath().split("/");
            Node current = root;
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (!path.isEmpty()) path.append('/');
                path.append(parts[i]);
                boolean file = i == parts.length - 1;
                current = current.child(parts[i], path.toString(), file ? "file" : "dir",
                        file ? asset : null);
            }
        }
        return root.children.stream().sorted(Node.ORDER).map(Node::view).toList();
    }

    private static final class Node {
        static final Comparator<Node> ORDER = Comparator.comparing((Node node) -> node.type.equals("file"))
                .thenComparing(node -> node.name.toLowerCase());
        final String name;
        final String path;
        final String type;
        final PackageAsset asset;
        final List<Node> children = new ArrayList<>();

        Node(String path, String type, String name, PackageAsset asset) {
            this.path = path;
            this.type = type;
            this.name = name;
            this.asset = asset;
        }

        Node child(String name, String path, String type, PackageAsset asset) {
            return children.stream().filter(node -> node.name.equals(name)).findFirst().orElseGet(() -> {
                Node node = new Node(path, type, name, asset);
                children.add(node);
                return node;
            });
        }

        Map<String, Object> view() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("type", type);
            if (asset != null) {
                result.put("size", asset.getSize());
                result.put("role", asset.getRole().name());
                result.put("mime_type", asset.getMimeType());
            }
            if (!children.isEmpty()) result.put("children", children.stream().sorted(ORDER).map(Node::view).toList());
            return result;
        }
    }
}
