package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.task.ParseTask;
import com.kbpack.task.ParseTaskRepository;
import com.kbpack.user.AppUser;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UploadService {

    private final UploadLimitService uploadLimitService;
    private final OperationLogService operationLogService;
    private final PackageService packageService;
    private final KnowledgePackageRepository packageRepository;
    private final PackageVersionRepository versionRepository;
    private final PackageAssetRepository assetRepository;
    private final ParseTaskRepository taskRepository;
    private final ObjectStorageService storage;

    public UploadService(
            UploadLimitService uploadLimitService,
            OperationLogService operationLogService,
            PackageService packageService,
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PackageAssetRepository assetRepository,
            ParseTaskRepository taskRepository,
            ObjectStorageService storage
    ) {
        this.uploadLimitService = uploadLimitService;
        this.operationLogService = operationLogService;
        this.packageService = packageService;
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.taskRepository = taskRepository;
        this.storage = storage;
    }

    public record UploadMetadata(
            String title,
            String description,
            KnowledgePackage.SourceType sourceType,
            String sourceName,
            String entryFile,
            UUID targetPackageId,
            List<String> tagNames,
            List<UUID> collectionIds,
            String requestIp
    ) {}

    public record UploadResult(KnowledgePackage knowledgePackage, PackageVersion version) {}

    private record InspectedFile(Path file, String path, long size, String sha256, String mimeType) {}

    @Transactional
    public UploadResult upload(MultipartFile multipart, UploadMetadata metadata, AppUser user) {
        if (multipart == null || multipart.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        String filename = safeFilename(multipart.getOriginalFilename());
        ArchiveType type = archiveType(filename);
        var limits = uploadLimitService.current();
        if (multipart.getSize() > limits.maxPackageSizeBytes()) {
            throw new ApiException(ErrorCode.UPLOAD_LIMIT_EXCEEDED, "上传文件超过 500 MB 限额");
        }

        Path work = null;
        List<String[]> storedObjects = new ArrayList<>();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) cleanupObjects(storedObjects);
                }
            });
        }
        try {
            work = Files.createTempDirectory("kbpack-upload-");
            Path original = work.resolve("original");
            String contentHash = copyOriginal(multipart, original, limits.maxPackageSizeBytes());
            validateMagic(original, type);

            KnowledgePackage pkg = resolvePackage(metadata, user);
            ensureNotDuplicate(pkg.getId(), contentHash);

            Path unpacked = Files.createDirectories(work.resolve("unpacked"));
            List<InspectedFile> files = inspectAndExtract(original, filename, type, unpacked, limits);
            if (files.isEmpty()) {
                throw new ApiException(ErrorCode.ARCHIVE_UNSAFE, "上传包中没有可用文件");
            }
            String entry = selectEntry(metadata.entryFile(), files);

            PackageVersion version = new PackageVersion();
            version.setPackageId(pkg.getId());
            version.setVersionNo(versionRepository.findMaxVersionNo(pkg.getId()) + 1);
            version.setOriginalFilename(filename);
            version.setContentHash(contentHash);
            version.setEntryFile(entry);
            version.setUnpackedSize(files.stream().mapToLong(InspectedFile::size).sum());
            version.setFileCount(files.size());
            version.setCreatedBy(user.getId());
            version.setParseStatus(PackageVersion.ParseStatus.pending);
            version.setStoragePath("pending");
            versionRepository.saveAndFlush(version);

            String packageId = IdPrefix.PACKAGE.format(pkg.getId());
            String versionId = IdPrefix.VERSION.format(version.getId());
            String extension = type == ArchiveType.TAR_GZ ? "tar.gz" : fileExtension(filename);
            String originalKey = packageId + "/" + versionId + "/original." + extension;
            storage.put(storage.originalBucket(), originalKey, Files.newInputStream(original), Files.size(original),
                    multipart.getContentType());
            storedObjects.add(new String[]{storage.originalBucket(), originalKey});

            for (InspectedFile file : files) {
                String objectKey = packageId + "/" + versionId + "/files/" + file.path();
                storage.put(storage.packagesBucket(), objectKey, Files.newInputStream(file.file()), file.size(), file.mimeType());
                storedObjects.add(new String[]{storage.packagesBucket(), objectKey});

                PackageAsset asset = new PackageAsset();
                asset.setVersionId(version.getId());
                asset.setPath(file.path());
                asset.setMimeType(file.mimeType());
                asset.setSize(file.size());
                asset.setSha256(file.sha256());
                asset.setRole(roleFor(file.path(), file.path().equals(entry)));
                assetRepository.save(asset);
            }

            version.setStoragePath(originalKey);
            versionRepository.save(version);
            pkg.setCurrentVersionId(version.getId());
            packageRepository.save(pkg);

            ParseTask task = new ParseTask();
            task.setVersionId(version.getId());
            task.setTaskType(ParseTask.TaskType.parse);
            task.setStatus(ParseTask.Status.pending);
            taskRepository.save(task);
            if (metadata.tagNames() != null && !metadata.tagNames().isEmpty()) {
                packageService.addTags(pkg.getId(), metadata.tagNames(), user, metadata.requestIp());
            }
            if (metadata.collectionIds() != null) {
                for (UUID collectionId : metadata.collectionIds()) {
                    packageService.addCollection(pkg.getId(), collectionId, user, metadata.requestIp());
                }
            }
            operationLogService.record(user.getId(), "package.upload", "package_version", version.getId(),
                    java.util.Map.of(
                            "package_id", pkg.getId().toString(),
                            "filename", filename,
                            "version_no", version.getVersionNo()
                    ), metadata.requestIp());
            return new UploadResult(pkg, version);
        } catch (ApiException e) {
            cleanupObjects(storedObjects);
            throw e;
        } catch (Exception e) {
            cleanupObjects(storedObjects);
            throw new ApiException(ErrorCode.ARCHIVE_UNSAFE, "上传包处理失败: " + rootMessage(e));
        } finally {
            deleteTree(work);
        }
    }

    private KnowledgePackage resolvePackage(UploadMetadata metadata, AppUser user) {
        if (metadata.targetPackageId() == null) {
            return packageService.createDraft(metadata.title(), metadata.description(), metadata.sourceType(),
                    metadata.sourceName(), user.getId());
        }
        KnowledgePackage pkg = packageRepository.findActiveById(metadata.targetPackageId())
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        boolean privileged = user.getRole() == AppUser.Role.owner || user.getRole() == AppUser.Role.admin;
        if (!pkg.getOwnerId().equals(user.getId()) && !privileged) {
            throw new ApiException(ErrorCode.PACKAGE_NOT_FOUND);
        }
        return pkg;
    }

    private void ensureNotDuplicate(UUID packageId, String hash) {
        boolean duplicate = versionRepository.findActiveByPackageId(packageId).stream()
                .anyMatch(v -> hash.equalsIgnoreCase(v.getContentHash()));
        if (duplicate) {
            throw new ApiException(ErrorCode.CONTENT_UNCHANGED);
        }
    }

    private String copyOriginal(MultipartFile multipart, Path target, long maxBytes) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream source = new DigestInputStream(new BufferedInputStream(multipart.getInputStream()), digest);
             var output = Files.newOutputStream(target)) {
            copyLimited(source, output, maxBytes, ErrorCode.UPLOAD_LIMIT_EXCEEDED);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private List<InspectedFile> inspectAndExtract(
            Path original,
            String filename,
            ArchiveType type,
            Path root,
            UploadLimitService.Limits limits
    ) throws IOException {
        List<InspectedFile> files = new ArrayList<>();
        Counter counter = new Counter();
        if (type == ArchiveType.HTML) {
            if (Files.size(original) > limits.maxSingleFileSizeBytes()) {
                throw new ApiException(ErrorCode.UPLOAD_LIMIT_EXCEEDED, "单个 HTML 文件超过大小限额");
            }
            Path target = root.resolve(filename);
            Files.copy(original, target, StandardCopyOption.REPLACE_EXISTING);
            files.add(inspectFile(target, filename));
            return files;
        }
        if (type == ArchiveType.ZIP) {
            try (var input = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(original)))) {
                ZipArchiveEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    if (!input.canReadEntryData(entry) || entry.isUnixSymlink()) {
                        throw unsafe(entry.getName(), "不支持的条目或符号链接");
                    }
                    extractEntry(input, entry.getName(), entry.isDirectory(), entry.getSize(), root, files, counter, limits);
                }
            }
        } else {
            try (var gzip = new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(original)));
                 var input = new TarArchiveInputStream(gzip)) {
                TarArchiveEntry entry;
                while ((entry = input.getNextEntry()) != null) {
                    if (entry.isSymbolicLink() || entry.isLink() || entry.isCharacterDevice()
                            || entry.isBlockDevice() || entry.isFIFO()) {
                        throw unsafe(entry.getName(), "压缩包包含链接或设备文件");
                    }
                    extractEntry(input, entry.getName(), entry.isDirectory(), entry.getSize(), root, files, counter, limits);
                }
            }
        }
        return files;
    }

    private void extractEntry(
            InputStream input,
            String rawName,
            boolean directory,
            long declaredSize,
            Path root,
            List<InspectedFile> files,
            Counter counter,
            UploadLimitService.Limits limits
    ) throws IOException {
        String name = normalizeEntryPath(rawName, limits.maxPathLength());
        if (name.isBlank() || directory) {
            return;
        }
        if (!counter.paths.add(name.toLowerCase(Locale.ROOT))) {
            throw unsafe(rawName, "压缩包包含重复路径");
        }
        if (++counter.files > limits.maxFileCount()) {
            throw new ApiException(ErrorCode.UPLOAD_LIMIT_EXCEEDED, "压缩包文件数超过 " + limits.maxFileCount());
        }
        if (declaredSize > limits.maxSingleFileSizeBytes()) {
            throw new ApiException(ErrorCode.UPLOAD_LIMIT_EXCEEDED, "单文件超过大小限额: " + name);
        }
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw unsafe(rawName, "路径逃逸");
        }
        Files.createDirectories(target.getParent());
        MessageDigest digest = sha256();
        try (var output = Files.newOutputStream(target)) {
            long copied = copyLimited(input, output, limits.maxSingleFileSizeBytes(), ErrorCode.UPLOAD_LIMIT_EXCEEDED);
            counter.bytes += copied;
            if (counter.bytes > limits.maxUnpackedSizeBytes()) {
                throw new ApiException(ErrorCode.UPLOAD_LIMIT_EXCEEDED, "解压后总大小超过 2 GB 限额");
            }
        }
        try (InputStream fileInput = Files.newInputStream(target)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = fileInput.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        files.add(new InspectedFile(target, name, Files.size(target), HexFormat.of().formatHex(digest.digest()),
                contentType(name)));
    }

    private InspectedFile inspectFile(Path file, String name) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return new InspectedFile(file, name, Files.size(file), HexFormat.of().formatHex(digest.digest()), contentType(name));
    }

    private static long copyLimited(InputStream input, java.io.OutputStream output, long limit, ErrorCode error)
            throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new ApiException(error, "文件内容超过配置限额");
            }
            output.write(buffer, 0, read);
        }
        return total;
    }

    private String selectEntry(String requested, List<InspectedFile> files) {
        if (requested != null && !requested.isBlank()) {
            String normalized = normalizeEntryPath(requested, uploadLimitService.current().maxPathLength());
            if (files.stream().noneMatch(f -> f.path().equals(normalized))) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "指定入口文件不存在: " + requested);
            }
            return normalized;
        }
        return files.stream().map(InspectedFile::path).filter(p -> p.equalsIgnoreCase("index.html"))
                .findFirst()
                .or(() -> files.stream().map(InspectedFile::path).filter(p -> p.equalsIgnoreCase("index.htm")).findFirst())
                .or(() -> files.stream().map(InspectedFile::path)
                        .filter(p -> p.matches("[^/]+/index\\.html?")).findFirst())
                .or(() -> files.stream().filter(f -> f.path().toLowerCase(Locale.ROOT).matches(".*\\.html?"))
                        .max(Comparator.comparingLong(InspectedFile::size)).map(InspectedFile::path))
                .orElse(null);
    }

    public static String normalizeEntryPath(String raw, int maxLength) {
        if (raw == null) {
            throw new ApiException(ErrorCode.ARCHIVE_UNSAFE, "压缩包包含空路径");
        }
        String value = raw.replace('\\', '/');
        if (value.length() > maxLength || value.startsWith("/") || value.matches("^[A-Za-z]:.*")
                || value.indexOf('\0') >= 0) {
            throw unsafe(raw, "绝对路径或路径过长");
        }
        Path normalized = Path.of(value).normalize();
        String result = normalized.toString().replace('\\', '/');
        if (result.equals("..") || result.startsWith("../") || value.contains("/../")) {
            throw unsafe(raw, "路径包含父目录跳转");
        }
        return result.equals(".") ? "" : result;
    }

    private static ApiException unsafe(String path, String reason) {
        return new ApiException(ErrorCode.ARCHIVE_UNSAFE, reason + ": " + path);
    }

    private static void validateMagic(Path file, ArchiveType type) throws IOException {
        byte[] header = new byte[8];
        int read;
        try (InputStream input = Files.newInputStream(file)) {
            read = input.read(header);
        }
        boolean zip = read >= 4 && header[0] == 'P' && header[1] == 'K';
        boolean gzip = read >= 2 && (header[0] & 0xff) == 0x1f && (header[1] & 0xff) == 0x8b;
        boolean html = false;
        if (type == ArchiveType.HTML) {
            byte[] sample;
            try (InputStream input = Files.newInputStream(file)) {
                sample = input.readNBytes(4096);
            }
            String prefix = new String(sample, java.nio.charset.StandardCharsets.UTF_8)
                    .stripLeading().toLowerCase(Locale.ROOT);
            html = prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.startsWith("<");
        }
        if ((type == ArchiveType.ZIP && !zip) || (type == ArchiveType.TAR_GZ && !gzip)
                || (type == ArchiveType.HTML && !html)) {
            throw new ApiException(ErrorCode.ARCHIVE_UNSAFE, "文件扩展名与实际内容不匹配");
        }
    }

    private static ArchiveType archiveType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) return ArchiveType.ZIP;
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return ArchiveType.TAR_GZ;
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return ArchiveType.HTML;
        throw new ApiException(ErrorCode.BAD_REQUEST, "仅支持 .zip、.tar.gz 和单个 HTML 文件");
    }

    private static PackageAsset.Role roleFor(String path, boolean entry) {
        if (entry) return PackageAsset.Role.entry;
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.html?")) return PackageAsset.Role.html;
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return PackageAsset.Role.markdown;
        if (lower.matches(".*\\.(png|jpe?g|gif|webp|svg|ico|avif)$")) return PackageAsset.Role.image;
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return PackageAsset.Role.script;
        if (lower.endsWith(".css")) return PackageAsset.Role.style;
        if (lower.matches(".*\\.(json|ya?ml|xml|csv)$")) return PackageAsset.Role.data;
        return PackageAsset.Role.other;
    }

    private static String contentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\.html?")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".md") || lower.endsWith(".txt")) return "text/plain; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.matches(".*\\.jpe?g")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private static String safeFilename(String raw) {
        String value = raw == null ? "upload.zip" : raw.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isBlank() || value.length() > 255) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "文件名无效");
        }
        return value;
    }

    private static String fileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "bin" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cleanupObjects(List<String[]> objects) {
        for (String[] object : objects.reversed()) {
            try { storage.remove(object[0], object[1]); } catch (Exception ignored) { }
        }
    }

    private static void deleteTree(Path root) {
        if (root == null) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static String rootMessage(Exception e) {
        Throwable current = e;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum ArchiveType { ZIP, TAR_GZ, HTML }
    private static final class Counter { int files; long bytes; final java.util.Set<String> paths = new HashSet<>(); }
}
