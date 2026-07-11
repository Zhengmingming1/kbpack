package com.kbpack.search;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.page.PageResponse;
import com.kbpack.parser.ExtractedDocument;
import com.kbpack.parser.ExtractedDocumentRepository;
import com.kbpack.parser.SearchChunk;
import com.kbpack.parser.SearchChunkRepository;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageCollectionRepository;
import com.kbpack.pkg.PackageTagRepository;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.pkg.CollectionRepository;
import com.kbpack.pkg.TagRepository;
import com.kbpack.user.AppUser;
import com.kbpack.user.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchIndexService indexService;
    private final SearchChunkRepository chunkRepository;
    private final ExtractedDocumentRepository documentRepository;
    private final KnowledgePackageRepository packageRepository;
    private final AuthService authService;
    private final PackageVersionRepository versionRepository;
    private final PackageTagRepository packageTagRepository;
    private final TagRepository tagRepository;
    private final PackageCollectionRepository packageCollectionRepository;
    private final CollectionRepository collectionRepository;
    private final OperationLogService operationLogService;

    public SearchController(
            SearchIndexService indexService,
            SearchChunkRepository chunkRepository,
            ExtractedDocumentRepository documentRepository,
            KnowledgePackageRepository packageRepository,
            AuthService authService,
            PackageVersionRepository versionRepository,
            PackageTagRepository packageTagRepository,
            TagRepository tagRepository,
            PackageCollectionRepository packageCollectionRepository,
            CollectionRepository collectionRepository,
            OperationLogService operationLogService
    ) {
        this.indexService = indexService;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.packageRepository = packageRepository;
        this.authService = authService;
        this.versionRepository = versionRepository;
        this.packageTagRepository = packageTagRepository;
        this.tagRepository = tagRepository;
        this.packageCollectionRepository = packageCollectionRepository;
        this.collectionRepository = collectionRepository;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public PageResponse<Map<String, Object>> search(
            @RequestParam String q,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String collection,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(name = "package_id", required = false) String packageId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            Authentication authentication
    ) {
        AppUser user = currentUser(authentication);
        if (q == null || q.isBlank()) throw new ApiException(ErrorCode.BAD_REQUEST, "q 不能为空");
        int currentPage = Math.max(1, page);
        int size = Math.min(100, Math.max(1, pageSize));
        String tagName = resolveTag(tag);
        String collectionId = resolveCollection(collection);
        String resolvedPackageId = resolvePackageId(packageId);
        String filter = buildFilter(user, tagName, collectionId, source, status, resolvedPackageId);
        try {
            SearchIndexService.SearchPage result = indexService.search(q.trim(), currentPage, size, filter);
            List<Map<String, Object>> visible = result.items().stream()
                    .filter(item -> visible(item, user)).toList();
            return PageResponse.of(result.total(), currentPage, size, visible);
        } catch (ApiException e) {
            if (e.getCode() != ErrorCode.SEARCH_UNAVAILABLE.getCode()) throw e;
            return fallback(q.trim(), currentPage, size, user, tagName, collectionId, source, status,
                    resolvedPackageId);
        }
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, String>> reindex(
            Authentication authentication,
            HttpServletRequest request
    ) {
        AppUser user = currentUser(authentication);
        if (user.getRole() != AppUser.Role.owner && user.getRole() != AppUser.Role.admin) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        indexService.reindexAll();
        operationLogService.record(
                user.getId(),
                "search.reindex",
                "search_index",
                null,
                Map.of(),
                request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", "reindex_started"));
    }

    private PageResponse<Map<String, Object>> fallback(
            String query, int page, int size, AppUser user,
            String tag, String collection, String source, String status, String packageId
    ) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> all = new ArrayList<>();
        for (SearchChunk chunk : chunkRepository.findAll()) {
            if (chunk.getContent() == null || !chunk.getContent().toLowerCase(Locale.ROOT).contains(needle)) continue;
            KnowledgePackage pkg = packageRepository.findActiveById(chunk.getPackageId()).orElse(null);
            if (pkg == null || !canAccess(pkg, user)) continue;
            if (packageId != null && !IdPrefix.PACKAGE.format(pkg.getId()).equals(packageId)) continue;
            if (versionRepository.findActiveById(chunk.getVersionId()).isEmpty()) continue;
            if (source != null && !source.isBlank() && !pkg.getSourceType().name().equalsIgnoreCase(source)) continue;
            if (status != null && !status.isBlank() && !pkg.getStatus().name().equalsIgnoreCase(status)) continue;
            if (tag != null && !packageHasTag(pkg, tag)) continue;
            if (collection != null && !packageHasCollection(pkg, collection)) continue;
            ExtractedDocument document = documentRepository.findById(chunk.getDocumentId()).orElse(null);
            if (document == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("package_id", IdPrefix.PACKAGE.format(pkg.getId()));
            item.put("package_title", pkg.getTitle());
            item.put("version_id", IdPrefix.VERSION.format(chunk.getVersionId()));
            item.put("document_id", IdPrefix.DOCUMENT.format(document.getId()));
            item.put("document_title", document.getTitle());
            item.put("snippet", highlight(snippet(chunk.getContent(), needle), query));
            item.put("heading", chunk.getHeading());
            item.put("tags", List.of());
            item.put("updated_at", pkg.getUpdatedAt());
            item.put("anchor", chunk.getAnchor());
            all.add(item);
        }
        all.sort(Comparator.comparing(item -> String.valueOf(item.get("updated_at")), Comparator.reverseOrder()));
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResponse.of(all.size(), page, size, all.subList(from, to));
    }

    private boolean visible(Map<String, Object> item, AppUser user) {
        try {
            var packageId = IdPrefix.PACKAGE.parse(String.valueOf(item.get("package_id")));
            var versionId = IdPrefix.VERSION.parse(String.valueOf(item.get("version_id")));
            return versionRepository.findActiveById(versionId).filter(version -> version.getPackageId().equals(packageId)).isPresent()
                    && packageRepository.findActiveById(packageId).map(pkg -> canAccess(pkg, user)).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canAccess(KnowledgePackage pkg, AppUser user) {
        return !"private".equals(pkg.getVisibility()) || pkg.getOwnerId().equals(user.getId())
                || user.getRole() == AppUser.Role.owner || user.getRole() == AppUser.Role.admin;
    }

    private AppUser currentUser(Authentication authentication) {
        return authService.requireUserById(authentication.getPrincipal().toString());
    }

    private String buildFilter(
            AppUser user,
            String tag,
            String collection,
            String source,
            String status,
            String packageId
    ) {
        List<String> filters = new ArrayList<>();
        if (user.getRole() != AppUser.Role.owner && user.getRole() != AppUser.Role.admin) {
            filters.add("(owner_id = " + quote(IdPrefix.USER.format(user.getId()))
                    + " OR visibility IN [\"team\", \"public\"])");
        }
        if (tag != null) filters.add("tags = " + quote(tag));
        if (collection != null) filters.add("collection_ids = " + quote(collection));
        if (source != null && !source.isBlank()) filters.add("source_type = " + quote(source.toLowerCase(Locale.ROOT)));
        if (status != null && !status.isBlank()) filters.add("status = " + quote(status.toLowerCase(Locale.ROOT)));
        if (packageId != null) filters.add("package_id = " + quote(packageId));
        return String.join(" AND ", filters);
    }

    private String resolvePackageId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return IdPrefix.PACKAGE.format(IdPrefix.PACKAGE.parse(value));
        } catch (IllegalArgumentException error) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "package_id 参数无效");
        }
    }

    private String resolveTag(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.regionMatches(true, 0, "tag_", 0, 4)) return value.trim();
        try {
            return tagRepository.findById(IdPrefix.TAG.parse(value)).map(tag -> tag.getName())
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "tag 参数无效"));
        } catch (IllegalArgumentException error) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "tag 参数无效");
        }
    }

    private String resolveCollection(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.regionMatches(true, 0, "col_", 0, 4)) {
            try {
                var id = IdPrefix.COLLECTION.parse(value);
                if (!collectionRepository.existsById(id)) throw new ApiException(ErrorCode.BAD_REQUEST, "collection 参数无效");
                return IdPrefix.COLLECTION.format(id);
            } catch (IllegalArgumentException error) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "collection 参数无效");
            }
        }
        return collectionRepository.findAll().stream().filter(item -> item.getName().equalsIgnoreCase(value.trim()))
                .findFirst().map(item -> IdPrefix.COLLECTION.format(item.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "collection 参数无效"));
    }

    private boolean packageHasTag(KnowledgePackage pkg, String name) {
        var ids = packageTagRepository.findAllByIdPackageId(pkg.getId()).stream()
                .map(link -> link.getId().getTagId()).toList();
        return tagRepository.findAllById(ids).stream().anyMatch(tag -> tag.getName().equalsIgnoreCase(name));
    }

    private boolean packageHasCollection(KnowledgePackage pkg, String collectionId) {
        return packageCollectionRepository.findAllByIdPackageId(pkg.getId()).stream()
                .anyMatch(link -> IdPrefix.COLLECTION.format(link.getId().getCollectionId()).equals(collectionId));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String snippet(String content, String needle) {
        int index = content.toLowerCase(Locale.ROOT).indexOf(needle);
        int start = Math.max(0, index - 100);
        int end = Math.min(content.length(), index + needle.length() + 180);
        return (start > 0 ? "..." : "") + content.substring(start, end) + (end < content.length() ? "..." : "");
    }

    private static String highlight(String text, String query) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(escaped).replaceAll(match -> "<em>" + Matcher.quoteReplacement(match.group()) + "</em>");
    }
}
