package com.kbpack.pkg;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionDiffServiceTest {

    @Mock private PackageAccessService accessService;
    @Mock private PackageVersionRepository versionRepository;
    @Mock private VersionDiffDocumentRepository documentRepository;
    @Mock private VersionDiffAssetRepository assetRepository;

    private VersionDiffService service;
    private UUID packageId;
    private UUID baseVersionId;
    private UUID targetVersionId;
    private AppUser actor;
    private KnowledgePackage pkg;

    @BeforeEach
    void setUp() {
        service = new VersionDiffService(
                accessService,
                versionRepository,
                documentRepository,
                assetRepository
        );
        packageId = UUID.randomUUID();
        baseVersionId = UUID.randomUUID();
        targetVersionId = UUID.randomUUID();
        actor = new AppUser();
        actor.setId(UUID.randomUUID());
        pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setCurrentVersionId(targetVersionId);
    }

    @Test
    void comparesVersionMetadataAndDocumentContent() {
        PackageVersion base = version(baseVersionId, 1, "base.zip", "archive-a");
        PackageVersion target = version(targetVersionId, 2, "target.zip", "archive-b");
        stubReadableVersions(base, target);

        String addedContent = "new line";
        String removedContent = "old line";
        String beforeContent = "same\nold value\ntail";
        String afterContent = "same\nnew value\ntail";
        List<VersionDiffDocumentRepository.DocumentPair> pairs = List.of(
                pair("added.md", null,
                        snapshot("added.md", "Added", addedContent, "added-hash")),
                pair("metadata.md",
                        snapshot("metadata.md", "Old title", "stable", "stable-hash"),
                        snapshot("metadata.md", "New title", "stable", "stable-hash")),
                pair("modified.md",
                        snapshot("modified.md", "Modified", beforeContent, "old-hash"),
                        snapshot("modified.md", "Modified", afterContent, "new-hash")),
                pair("removed.md",
                        snapshot("removed.md", "Removed", removedContent, "removed-hash"), null),
                pair("unchanged.md",
                        snapshot("unchanged.md", "Same", "stable", "same-hash"),
                        snapshot("unchanged.md", "Same", "stable", "same-hash"))
        );
        when(documentRepository.countDistinctPaths(baseVersionId, targetVersionId)).thenReturn(5L);
        when(documentRepository.loadDocumentPairs(
                baseVersionId, targetVersionId, VersionDiffService.MAX_DOCUMENTS + 1
        )).thenReturn(pairs);
        when(documentRepository.loadContentPreview(
                targetVersionId, "added.md", VersionDiffService.MAX_SOURCE_CHARACTERS + 1
        )).thenReturn(Optional.of(addedContent));
        when(documentRepository.loadContentPreview(
                baseVersionId, "modified.md", VersionDiffService.MAX_SOURCE_CHARACTERS + 1
        )).thenReturn(Optional.of(beforeContent));
        when(documentRepository.loadContentPreview(
                targetVersionId, "modified.md", VersionDiffService.MAX_SOURCE_CHARACTERS + 1
        )).thenReturn(Optional.of(afterContent));
        when(documentRepository.loadContentPreview(
                baseVersionId, "removed.md", VersionDiffService.MAX_SOURCE_CHARACTERS + 1
        )).thenReturn(Optional.of(removedContent));

        VersionDiffService.VersionDiffResponse response = service.compare(
                packageId, baseVersionId, targetVersionId, actor
        );

        assertThat(response.base_version().version_no()).isEqualTo(1);
        assertThat(response.target_version().is_current()).isTrue();
        assertThat(response.metadata_changes())
                .extracting(VersionDiffService.MetadataChange::field)
                .contains("version_no", "original_filename", "content_hash");
        assertThat(response.document_summary().added()).isEqualTo(1);
        assertThat(response.document_summary().removed()).isEqualTo(1);
        assertThat(response.document_summary().modified()).isEqualTo(2);
        assertThat(response.document_summary().unchanged()).isEqualTo(1);
        assertThat(response.document_changes()).hasSize(4);
        assertThat(response.truncated()).isFalse();

        VersionDiffService.DocumentChange metadataOnly = change(response, "metadata.md");
        assertThat(metadataOnly.fields_changed()).containsExactly("title");
        assertThat(metadataOnly.content_diff().status()).isEqualTo("unchanged");

        VersionDiffService.DocumentChange modified = change(response, "modified.md");
        assertThat(modified.content_diff().hunks()).isNotEmpty();
        assertThat(modified.content_diff().hunks().getFirst().lines())
                .extracting(VersionDiffService.DiffLine::type)
                .contains("removed", "added");
        verify(accessService).requireReadable(packageId, actor);
    }

    @Test
    void omitsDetailedContentAfterTheDocumentLimit() {
        PackageVersion base = version(baseVersionId, 1, "base.zip", "archive-a");
        PackageVersion target = version(targetVersionId, 2, "target.zip", "archive-b");
        stubReadableVersions(base, target);

        List<VersionDiffDocumentRepository.DocumentPair> pairs = new ArrayList<>();
        for (int index = 0; index <= VersionDiffService.MAX_DETAILED_DOCUMENTS; index++) {
            String path = "added-%02d.md".formatted(index);
            pairs.add(pair(path, null, snapshot(path, "Added", "new", "hash-" + index)));
        }
        when(documentRepository.countDistinctPaths(baseVersionId, targetVersionId))
                .thenReturn((long) pairs.size());
        when(documentRepository.loadDocumentPairs(
                baseVersionId, targetVersionId, VersionDiffService.MAX_DOCUMENTS + 1
        )).thenReturn(pairs);
        when(documentRepository.loadContentPreview(any(), anyString(), anyInt()))
                .thenReturn(Optional.of("new"));

        VersionDiffService.VersionDiffResponse response = service.compare(
                packageId, baseVersionId, targetVersionId, actor
        );

        assertThat(response.document_changes())
                .extracting(change -> change.content_diff().status())
                .filteredOn("available"::equals)
                .hasSize(VersionDiffService.MAX_DETAILED_DOCUMENTS);
        assertThat(response.document_changes())
                .extracting(change -> change.content_diff().status())
                .containsOnlyOnce("omitted_limit");
        assertThat(response.truncated()).isTrue();
    }

    @Test
    void truncatesLongRenderedLinesAndReportsTheLimit() {
        PackageVersion base = version(baseVersionId, 1, "base.zip", "archive-a");
        PackageVersion target = version(targetVersionId, 2, "target.zip", "archive-b");
        stubReadableVersions(base, target);
        String longLine = "x".repeat(VersionDiffService.MAX_LINE_CHARACTERS + 25);
        var pair = pair(
                "long.md",
                snapshot("long.md", "Long", "old", "old-hash"),
                snapshot("long.md", "Long", longLine, "new-hash")
        );
        when(documentRepository.countDistinctPaths(baseVersionId, targetVersionId)).thenReturn(1L);
        when(documentRepository.loadDocumentPairs(
                baseVersionId, targetVersionId, VersionDiffService.MAX_DOCUMENTS + 1
        )).thenReturn(List.of(pair));
        when(documentRepository.loadContentPreview(
                baseVersionId, "long.md", VersionDiffService.MAX_SOURCE_CHARACTERS + 1
        )).thenReturn(Optional.of("old"));
        when(documentRepository.loadContentPreview(
                targetVersionId, "long.md", VersionDiffService.MAX_SOURCE_CHARACTERS + 1
        )).thenReturn(Optional.of(longLine));

        VersionDiffService.VersionDiffResponse response = service.compare(
                packageId, baseVersionId, targetVersionId, actor
        );

        var contentDiff = response.document_changes().getFirst().content_diff();
        assertThat(contentDiff.truncated()).isTrue();
        assertThat(contentDiff.hunks().getFirst().lines())
                .extracting(line -> line.text().codePointCount(0, line.text().length()))
                .allMatch(length -> length <= VersionDiffService.MAX_LINE_CHARACTERS);
        assertThat(response.limits().max_line_characters())
                .isEqualTo(VersionDiffService.MAX_LINE_CHARACTERS);
    }

    @Test
    void rejectsAVersionThatDoesNotBelongToThePackage() {
        PackageVersion base = version(baseVersionId, 1, "base.zip", "archive-a");
        when(accessService.requireReadable(packageId, actor)).thenReturn(pkg);
        when(versionRepository.findActiveByIdAndPackageId(baseVersionId, packageId))
                .thenReturn(Optional.of(base));
        when(versionRepository.findActiveByIdAndPackageId(targetVersionId, packageId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.compare(
                packageId, baseVersionId, targetVersionId, actor
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VERSION_NOT_FOUND));
        verify(documentRepository, never()).loadDocumentPairs(any(), any(), anyInt());
    }

    @Test
    void comparesPackageAssets() {
        PackageVersion base = version(baseVersionId, 1, "base.zip", "archive-a");
        PackageVersion target = version(targetVersionId, 2, "target.zip", "archive-b");
        stubReadableVersions(base, target);
        when(documentRepository.countDistinctPaths(baseVersionId, targetVersionId)).thenReturn(0L);
        when(documentRepository.loadDocumentPairs(
                baseVersionId, targetVersionId, VersionDiffService.MAX_DOCUMENTS + 1
        )).thenReturn(List.of());
        List<VersionDiffAssetRepository.AssetPair> assets = List.of(
                assetPair("added.css", null,
                        asset("added.css", "text/css", 10, "added-hash", "style")),
                assetPair("modified.js",
                        asset("modified.js", "text/javascript", 20, "old-hash", "script"),
                        asset("modified.js", "text/javascript", 25, "new-hash", "script")),
                assetPair("removed.png",
                        asset("removed.png", "image/png", 30, "removed-hash", "image"), null),
                assetPair("same.md",
                        asset("same.md", "text/markdown", 40, "same-hash", "markdown"),
                        asset("same.md", "text/markdown", 40, "same-hash", "markdown"))
        );
        when(assetRepository.countDistinctPaths(baseVersionId, targetVersionId)).thenReturn(4L);
        when(assetRepository.loadAssetPairs(
                baseVersionId, targetVersionId, VersionDiffService.MAX_ASSETS + 1
        )).thenReturn(assets);

        VersionDiffService.VersionDiffResponse response = service.compare(
                packageId, baseVersionId, targetVersionId, actor
        );

        assertThat(response.asset_summary().added()).isEqualTo(1);
        assertThat(response.asset_summary().removed()).isEqualTo(1);
        assertThat(response.asset_summary().modified()).isEqualTo(1);
        assertThat(response.asset_summary().unchanged()).isEqualTo(1);
        assertThat(response.asset_changes()).hasSize(3);
        VersionDiffService.AssetChange modified = response.asset_changes().stream()
                .filter(change -> change.path().equals("modified.js"))
                .findFirst()
                .orElseThrow();
        assertThat(modified.fields_changed()).containsExactly("sha256", "size");
        assertThat(modified.before().sha256()).isEqualTo("sha256:old-hash");
    }

    @Test
    void rejectsVersionThatHasNotParsedSuccessfully() {
        PackageVersion base = version(baseVersionId, 1, "base.zip", "archive-a");
        base.setParseStatus(PackageVersion.ParseStatus.processing);
        when(accessService.requireReadable(packageId, actor)).thenReturn(pkg);
        when(versionRepository.findActiveByIdAndPackageId(baseVersionId, packageId))
                .thenReturn(Optional.of(base));

        assertThatThrownBy(() -> service.compare(
                packageId, baseVersionId, targetVersionId, actor
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(versionRepository, never()).findActiveByIdAndPackageId(targetVersionId, packageId);
        verify(documentRepository, never()).countDistinctPaths(any(), any());
        verify(assetRepository, never()).countDistinctPaths(any(), any());
    }

    private void stubReadableVersions(PackageVersion base, PackageVersion target) {
        when(accessService.requireReadable(packageId, actor)).thenReturn(pkg);
        when(versionRepository.findActiveByIdAndPackageId(baseVersionId, packageId))
                .thenReturn(Optional.of(base));
        when(versionRepository.findActiveByIdAndPackageId(targetVersionId, packageId))
                .thenReturn(Optional.of(target));
    }

    private PackageVersion version(UUID id, int number, String filename, String hash) {
        PackageVersion version = new PackageVersion();
        version.setId(id);
        version.setPackageId(packageId);
        version.setVersionNo(number);
        version.setOriginalFilename(filename);
        version.setContentHash(hash);
        version.setParseStatus(PackageVersion.ParseStatus.success);
        return version;
    }

    private VersionDiffDocumentRepository.DocumentPair pair(
            String path,
            VersionDiffDocumentRepository.DocumentSnapshot base,
            VersionDiffDocumentRepository.DocumentSnapshot target
    ) {
        return new VersionDiffDocumentRepository.DocumentPair(path, base, target);
    }

    private VersionDiffAssetRepository.AssetPair assetPair(
            String path,
            VersionDiffAssetRepository.AssetSnapshot base,
            VersionDiffAssetRepository.AssetSnapshot target
    ) {
        return new VersionDiffAssetRepository.AssetPair(path, base, target);
    }

    private VersionDiffAssetRepository.AssetSnapshot asset(
            String path,
            String mimeType,
            long size,
            String hash,
            String role
    ) {
        return new VersionDiffAssetRepository.AssetSnapshot(path, mimeType, size, hash, role);
    }

    private VersionDiffDocumentRepository.DocumentSnapshot snapshot(
            String path,
            String title,
            String content,
            String hash
    ) {
        return new VersionDiffDocumentRepository.DocumentSnapshot(
                path,
                title,
                "markdown",
                1,
                content.length(),
                content.codePointCount(0, content.length()),
                hash
        );
    }

    private VersionDiffService.DocumentChange change(
            VersionDiffService.VersionDiffResponse response,
            String path
    ) {
        return response.document_changes().stream()
                .filter(change -> path.equals(change.source_path()))
                .findFirst()
                .orElseThrow();
    }
}
