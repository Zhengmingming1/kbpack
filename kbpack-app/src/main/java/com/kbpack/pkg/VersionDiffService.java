package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class VersionDiffService {

    static final int MAX_DOCUMENTS = 500;
    static final int MAX_ASSETS = 1_000;
    static final int MAX_DETAILED_DOCUMENTS = 20;
    static final int MAX_SOURCE_CHARACTERS = 50_000;
    static final int MAX_SOURCE_LINES = 500;
    static final int MAX_DIFF_LINES_PER_DOCUMENT = 500;
    static final int MAX_DIFF_LINES_TOTAL = 4_000;
    static final int MAX_LINE_CHARACTERS = 500;
    static final int CONTEXT_LINES = 3;

    public record VersionDiffResponse(
            String package_id,
            VersionReference base_version,
            VersionReference target_version,
            List<MetadataChange> metadata_changes,
            DocumentSummary document_summary,
            List<DocumentChange> document_changes,
            AssetSummary asset_summary,
            List<AssetChange> asset_changes,
            boolean truncated,
            DiffLimits limits
    ) {
    }

    public record VersionReference(
            String version_id,
            int version_no,
            String original_filename,
            String content_hash,
            String entry_file,
            Long unpacked_size,
            Integer file_count,
            String parse_status,
            Instant created_at,
            boolean is_current
    ) {
    }

    public record MetadataChange(
            String field,
            String change_type,
            Object before,
            Object after
    ) {
    }

    public record DocumentSummary(
            long total_paths,
            int compared_paths,
            int returned,
            int added,
            int removed,
            int modified,
            int unchanged
    ) {
    }

    public record DocumentChange(
            String source_path,
            String change_type,
            List<String> fields_changed,
            DocumentMetadata before,
            DocumentMetadata after,
            ContentDiff content_diff
    ) {
    }

    public record AssetSummary(
            long total_paths,
            int compared_paths,
            int returned,
            int added,
            int removed,
            int modified,
            int unchanged
    ) {
    }

    public record AssetChange(
            String path,
            String change_type,
            List<String> fields_changed,
            AssetMetadata before,
            AssetMetadata after
    ) {
    }

    public record AssetMetadata(
            String mime_type,
            long size,
            String sha256,
            String role
    ) {
    }

    public record DocumentMetadata(
            String title,
            String doc_type,
            Integer order_no,
            Integer word_count,
            long content_length,
            String content_hash
    ) {
    }

    public record ContentDiff(
            String status,
            boolean changed,
            boolean truncated,
            List<DiffHunk> hunks
    ) {
    }

    public record DiffHunk(
            int old_start,
            int old_count,
            int new_start,
            int new_count,
            List<DiffLine> lines
    ) {
    }

    public record DiffLine(
            String type,
            Integer old_line,
            Integer new_line,
            String text
    ) {
    }

    public record DiffLimits(
            int max_documents,
            int max_assets,
            int max_detailed_documents,
            int max_source_characters_per_document,
            int max_source_lines_per_document,
            int max_diff_lines_per_document,
            int max_diff_lines_total,
            int max_line_characters,
            int context_lines
    ) {
    }

    private record PreparedText(List<String> lines, boolean truncated) {
    }

    private record DiffOperation(String type, Integer oldLine, Integer newLine, String text) {
    }

    private record DiffBuild(ContentDiff contentDiff, int outputLines) {
    }

    private record AssetDiffBuild(
            AssetSummary summary,
            List<AssetChange> changes,
            boolean truncated
    ) {
    }

    private final PackageAccessService accessService;
    private final PackageVersionRepository versionRepository;
    private final VersionDiffDocumentRepository documentRepository;
    private final VersionDiffAssetRepository assetRepository;

    public VersionDiffService(
            PackageAccessService accessService,
            PackageVersionRepository versionRepository,
            VersionDiffDocumentRepository documentRepository,
            VersionDiffAssetRepository assetRepository
    ) {
        this.accessService = accessService;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public VersionDiffResponse compare(
            UUID packageId,
            UUID baseVersionId,
            UUID targetVersionId,
            AppUser actor
    ) {
        KnowledgePackage pkg = accessService.requireReadable(packageId, actor);
        PackageVersion base = requireVersion(packageId, baseVersionId);
        PackageVersion target = requireVersion(packageId, targetVersionId);

        long totalPaths = documentRepository.countDistinctPaths(baseVersionId, targetVersionId);
        List<VersionDiffDocumentRepository.DocumentPair> loaded = new ArrayList<>(
                documentRepository.loadDocumentPairs(
                        baseVersionId,
                        targetVersionId,
                        MAX_DOCUMENTS + 1
                )
        );
        boolean documentListTruncated = loaded.size() > MAX_DOCUMENTS || totalPaths > MAX_DOCUMENTS;
        List<VersionDiffDocumentRepository.DocumentPair> pairs = loaded.size() > MAX_DOCUMENTS
                ? List.copyOf(loaded.subList(0, MAX_DOCUMENTS))
                : List.copyOf(loaded);

        int added = 0;
        int removed = 0;
        int modified = 0;
        int unchanged = 0;
        int detailedDocuments = 0;
        int remainingDiffLines = MAX_DIFF_LINES_TOTAL;
        boolean contentTruncated = false;
        List<DocumentChange> changes = new ArrayList<>();

        for (VersionDiffDocumentRepository.DocumentPair pair : pairs) {
            var before = pair.base();
            var after = pair.target();
            String changeType;
            List<String> fieldsChanged;
            boolean contentChanged;

            if (before == null) {
                added++;
                changeType = "added";
                fieldsChanged = List.of("document", "content");
                contentChanged = true;
            } else if (after == null) {
                removed++;
                changeType = "removed";
                fieldsChanged = List.of("document", "content");
                contentChanged = true;
            } else {
                fieldsChanged = changedDocumentFields(before, after);
                if (fieldsChanged.isEmpty()) {
                    unchanged++;
                    continue;
                }
                modified++;
                changeType = "modified";
                contentChanged = fieldsChanged.contains("content");
            }

            ContentDiff contentDiff;
            if (!contentChanged) {
                contentDiff = new ContentDiff("unchanged", false, false, List.of());
            } else if (detailedDocuments >= MAX_DETAILED_DOCUMENTS || remainingDiffLines <= 0) {
                contentDiff = new ContentDiff("omitted_limit", true, true, List.of());
            } else {
                DiffBuild build = buildContentDiff(
                        baseVersionId,
                        targetVersionId,
                        pair,
                        remainingDiffLines
                );
                contentDiff = build.contentDiff();
                remainingDiffLines -= build.outputLines();
                detailedDocuments++;
            }
            contentTruncated |= contentDiff.truncated();
            changes.add(new DocumentChange(
                    pair.sourcePath(),
                    changeType,
                    fieldsChanged,
                    documentMetadata(before),
                    documentMetadata(after),
                    contentDiff
            ));
        }

        DocumentSummary summary = new DocumentSummary(
                totalPaths,
                pairs.size(),
                changes.size(),
                added,
                removed,
                modified,
                unchanged
        );
        AssetDiffBuild assetDiff = buildAssetDiff(baseVersionId, targetVersionId);
        return new VersionDiffResponse(
                IdPrefix.PACKAGE.format(packageId),
                versionReference(base, pkg.getCurrentVersionId()),
                versionReference(target, pkg.getCurrentVersionId()),
                metadataChanges(base, target),
                summary,
                List.copyOf(changes),
                assetDiff.summary(),
                assetDiff.changes(),
                documentListTruncated || contentTruncated || assetDiff.truncated(),
                limits()
        );
    }

    private PackageVersion requireVersion(UUID packageId, UUID versionId) {
        PackageVersion version = versionRepository.findActiveByIdAndPackageId(versionId, packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.VERSION_NOT_FOUND));
        if (version.getParseStatus() != PackageVersion.ParseStatus.success) {
            throw new ApiException(ErrorCode.CONFLICT, "版本尚未解析成功，无法比较内容");
        }
        return version;
    }

    private AssetDiffBuild buildAssetDiff(UUID baseVersionId, UUID targetVersionId) {
        long totalPaths = assetRepository.countDistinctPaths(baseVersionId, targetVersionId);
        List<VersionDiffAssetRepository.AssetPair> loaded = new ArrayList<>(
                assetRepository.loadAssetPairs(baseVersionId, targetVersionId, MAX_ASSETS + 1)
        );
        boolean truncated = loaded.size() > MAX_ASSETS || totalPaths > MAX_ASSETS;
        List<VersionDiffAssetRepository.AssetPair> pairs = loaded.size() > MAX_ASSETS
                ? List.copyOf(loaded.subList(0, MAX_ASSETS))
                : List.copyOf(loaded);

        int added = 0;
        int removed = 0;
        int modified = 0;
        int unchanged = 0;
        List<AssetChange> changes = new ArrayList<>();
        for (VersionDiffAssetRepository.AssetPair pair : pairs) {
            var before = pair.base();
            var after = pair.target();
            String changeType;
            List<String> fieldsChanged;
            if (before == null) {
                added++;
                changeType = "added";
                fieldsChanged = List.of("asset");
            } else if (after == null) {
                removed++;
                changeType = "removed";
                fieldsChanged = List.of("asset");
            } else {
                fieldsChanged = changedAssetFields(before, after);
                if (fieldsChanged.isEmpty()) {
                    unchanged++;
                    continue;
                }
                modified++;
                changeType = "modified";
            }
            changes.add(new AssetChange(
                    pair.path(),
                    changeType,
                    fieldsChanged,
                    assetMetadata(before),
                    assetMetadata(after)
            ));
        }
        return new AssetDiffBuild(
                new AssetSummary(
                        totalPaths,
                        pairs.size(),
                        changes.size(),
                        added,
                        removed,
                        modified,
                        unchanged
                ),
                List.copyOf(changes),
                truncated
        );
    }

    private List<String> changedAssetFields(
            VersionDiffAssetRepository.AssetSnapshot before,
            VersionDiffAssetRepository.AssetSnapshot after
    ) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(before.sha256(), after.sha256())) fields.add("sha256");
        if (before.size() != after.size()) fields.add("size");
        if (!Objects.equals(before.mimeType(), after.mimeType())) fields.add("mime_type");
        if (!Objects.equals(before.role(), after.role())) fields.add("role");
        return List.copyOf(fields);
    }

    private AssetMetadata assetMetadata(VersionDiffAssetRepository.AssetSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new AssetMetadata(
                snapshot.mimeType(),
                snapshot.size(),
                formatHash(snapshot.sha256()),
                snapshot.role()
        );
    }

    private DiffBuild buildContentDiff(
            UUID baseVersionId,
            UUID targetVersionId,
            VersionDiffDocumentRepository.DocumentPair pair,
            int remainingDiffLines
    ) {
        Optional<PreparedText> before = loadPreparedText(baseVersionId, pair.sourcePath(), pair.base());
        Optional<PreparedText> after = loadPreparedText(targetVersionId, pair.sourcePath(), pair.target());
        if (before.isEmpty() || after.isEmpty()) {
            return new DiffBuild(
                    new ContentDiff("unavailable", true, true, List.of()),
                    0
            );
        }

        List<DiffOperation> operations = lineDiff(before.get().lines(), after.get().lines());
        List<int[]> ranges = hunkRanges(operations);
        int allowedLines = Math.min(MAX_DIFF_LINES_PER_DOCUMENT, remainingDiffLines);
        int outputLines = 0;
        boolean truncated = before.get().truncated() || after.get().truncated();
        List<DiffHunk> hunks = new ArrayList<>();

        for (int[] range : ranges) {
            if (outputLines >= allowedLines) {
                truncated = true;
                break;
            }
            int start = range[0];
            int end = Math.min(range[1], start + allowedLines - outputLines);
            if (end < range[1]) {
                truncated = true;
            }
            List<DiffLine> lines = new ArrayList<>();
            for (int index = start; index < end; index++) {
                DiffOperation operation = operations.get(index);
                String rendered = truncateCodePoints(operation.text(), MAX_LINE_CHARACTERS);
                if (!rendered.equals(operation.text())) {
                    truncated = true;
                }
                lines.add(new DiffLine(
                        operation.type(),
                        operation.oldLine(),
                        operation.newLine(),
                        rendered
                ));
            }
            if (!lines.isEmpty()) {
                hunks.add(toHunk(lines));
                outputLines += lines.size();
            }
            if (end < range[1]) {
                break;
            }
        }
        return new DiffBuild(
                new ContentDiff("available", true, truncated, List.copyOf(hunks)),
                outputLines
        );
    }

    private Optional<PreparedText> loadPreparedText(
            UUID versionId,
            String sourcePath,
            VersionDiffDocumentRepository.DocumentSnapshot snapshot
    ) {
        if (snapshot == null) {
            return Optional.of(new PreparedText(List.of(), false));
        }
        return documentRepository.loadContentPreview(
                        versionId,
                        sourcePath,
                        MAX_SOURCE_CHARACTERS + 1
                )
                .map(content -> prepareText(content, snapshot.contentLength()));
    }

    private PreparedText prepareText(String source, long fullLength) {
        int codePoints = source.codePointCount(0, source.length());
        boolean truncated = fullLength > MAX_SOURCE_CHARACTERS || codePoints > MAX_SOURCE_CHARACTERS;
        String bounded = truncateCodePoints(source, MAX_SOURCE_CHARACTERS);
        String[] split = bounded.split("\\R", -1);
        if (split.length > MAX_SOURCE_LINES) {
            truncated = true;
        }
        int lineCount = Math.min(split.length, MAX_SOURCE_LINES);
        List<String> lines = new ArrayList<>(lineCount);
        for (int index = 0; index < lineCount; index++) {
            lines.add(split[index]);
        }
        return new PreparedText(List.copyOf(lines), truncated);
    }

    private List<DiffOperation> lineDiff(List<String> before, List<String> after) {
        int oldSize = before.size();
        int newSize = after.size();
        short[][] lcs = new short[oldSize + 1][newSize + 1];
        for (int oldIndex = oldSize - 1; oldIndex >= 0; oldIndex--) {
            for (int newIndex = newSize - 1; newIndex >= 0; newIndex--) {
                if (before.get(oldIndex).equals(after.get(newIndex))) {
                    lcs[oldIndex][newIndex] = (short) (lcs[oldIndex + 1][newIndex + 1] + 1);
                } else {
                    lcs[oldIndex][newIndex] = (short) Math.max(
                            lcs[oldIndex + 1][newIndex],
                            lcs[oldIndex][newIndex + 1]
                    );
                }
            }
        }

        List<DiffOperation> operations = new ArrayList<>(oldSize + newSize);
        int oldIndex = 0;
        int newIndex = 0;
        while (oldIndex < oldSize || newIndex < newSize) {
            if (oldIndex < oldSize && newIndex < newSize
                    && before.get(oldIndex).equals(after.get(newIndex))) {
                operations.add(new DiffOperation(
                        "context", oldIndex + 1, newIndex + 1, before.get(oldIndex)
                ));
                oldIndex++;
                newIndex++;
            } else if (oldIndex < oldSize && (newIndex == newSize
                    || lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1])) {
                operations.add(new DiffOperation(
                        "removed", oldIndex + 1, null, before.get(oldIndex)
                ));
                oldIndex++;
            } else {
                operations.add(new DiffOperation(
                        "added", null, newIndex + 1, after.get(newIndex)
                ));
                newIndex++;
            }
        }
        return operations;
    }

    private List<int[]> hunkRanges(List<DiffOperation> operations) {
        List<int[]> ranges = new ArrayList<>();
        for (int index = 0; index < operations.size(); index++) {
            if ("context".equals(operations.get(index).type())) {
                continue;
            }
            int start = Math.max(0, index - CONTEXT_LINES);
            int end = Math.min(operations.size(), index + CONTEXT_LINES + 1);
            if (!ranges.isEmpty() && start <= ranges.getLast()[1]) {
                ranges.getLast()[1] = Math.max(ranges.getLast()[1], end);
            } else {
                ranges.add(new int[]{start, end});
            }
        }
        return ranges;
    }

    private DiffHunk toHunk(List<DiffLine> lines) {
        int oldStart = lines.stream()
                .map(DiffLine::old_line)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0);
        int newStart = lines.stream()
                .map(DiffLine::new_line)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0);
        int oldCount = (int) lines.stream().filter(line -> line.old_line() != null).count();
        int newCount = (int) lines.stream().filter(line -> line.new_line() != null).count();
        return new DiffHunk(oldStart, oldCount, newStart, newCount, List.copyOf(lines));
    }

    private List<String> changedDocumentFields(
            VersionDiffDocumentRepository.DocumentSnapshot before,
            VersionDiffDocumentRepository.DocumentSnapshot after
    ) {
        List<String> fields = new ArrayList<>();
        if (!Objects.equals(before.contentHash(), after.contentHash())) fields.add("content");
        if (!Objects.equals(before.title(), after.title())) fields.add("title");
        if (!Objects.equals(before.docType(), after.docType())) fields.add("doc_type");
        if (!Objects.equals(before.orderNo(), after.orderNo())) fields.add("order_no");
        if (!Objects.equals(before.wordCount(), after.wordCount())) fields.add("word_count");
        return List.copyOf(fields);
    }

    private DocumentMetadata documentMetadata(
            VersionDiffDocumentRepository.DocumentSnapshot snapshot
    ) {
        if (snapshot == null) {
            return null;
        }
        return new DocumentMetadata(
                snapshot.title(),
                snapshot.docType(),
                snapshot.orderNo(),
                snapshot.wordCount(),
                snapshot.contentLength(),
                formatHash(snapshot.contentHash())
        );
    }

    private List<MetadataChange> metadataChanges(PackageVersion base, PackageVersion target) {
        List<MetadataChange> changes = new ArrayList<>();
        addMetadataChange(changes, "version_no", base.getVersionNo(), target.getVersionNo());
        addMetadataChange(changes, "original_filename",
                base.getOriginalFilename(), target.getOriginalFilename());
        addMetadataChange(changes, "content_hash",
                formatHash(base.getContentHash()), formatHash(target.getContentHash()));
        addMetadataChange(changes, "entry_file", base.getEntryFile(), target.getEntryFile());
        addMetadataChange(changes, "unpacked_size", base.getUnpackedSize(), target.getUnpackedSize());
        addMetadataChange(changes, "file_count", base.getFileCount(), target.getFileCount());
        addMetadataChange(changes, "parse_status",
                enumName(base.getParseStatus()), enumName(target.getParseStatus()));
        return List.copyOf(changes);
    }

    private void addMetadataChange(
            List<MetadataChange> changes,
            String field,
            Object before,
            Object after
    ) {
        if (Objects.equals(before, after)) {
            return;
        }
        String type = before == null ? "added" : after == null ? "removed" : "modified";
        changes.add(new MetadataChange(field, type, before, after));
    }

    private VersionReference versionReference(PackageVersion version, UUID currentVersionId) {
        return new VersionReference(
                IdPrefix.VERSION.format(version.getId()),
                version.getVersionNo(),
                version.getOriginalFilename(),
                formatHash(version.getContentHash()),
                version.getEntryFile(),
                version.getUnpackedSize(),
                version.getFileCount(),
                enumName(version.getParseStatus()),
                version.getCreatedAt(),
                version.getId().equals(currentVersionId)
        );
    }

    private DiffLimits limits() {
        return new DiffLimits(
                MAX_DOCUMENTS,
                MAX_ASSETS,
                MAX_DETAILED_DOCUMENTS,
                MAX_SOURCE_CHARACTERS,
                MAX_SOURCE_LINES,
                MAX_DIFF_LINES_PER_DOCUMENT,
                MAX_DIFF_LINES_TOTAL,
                MAX_LINE_CHARACTERS,
                CONTEXT_LINES
        );
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private String formatHash(String hash) {
        if (hash == null || hash.startsWith("sha256:")) {
            return hash;
        }
        return "sha256:" + hash;
    }

    private String truncateCodePoints(String value, int maximum) {
        if (value == null || value.codePointCount(0, value.length()) <= maximum) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }
}
