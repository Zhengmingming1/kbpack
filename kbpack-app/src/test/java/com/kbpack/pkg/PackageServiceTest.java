package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PackageServiceTest {

    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private PackageVersionRepository versionRepository;
    @Mock private PackageAssetRepository assetRepository;
    @Mock private TagRepository tagRepository;
    @Mock private PackageTagRepository packageTagRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private PackageCollectionRepository packageCollectionRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private PackageAccessService accessService;
    @Mock private OperationLogService operationLogService;
    @Mock private SearchIndexUpdateCoordinator searchIndexUpdates;

    @InjectMocks
    private PackageService packageService;

    @Test
    void refusesToDeleteCurrentVersion() {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        actor.setRole(AppUser.Role.owner);

        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setCurrentVersionId(versionId);
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);

        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));

        assertThatThrownBy(() -> packageService.deleteVersion(packageId, versionId, actor, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.CURRENT_VERSION_DELETE_FORBIDDEN));
        verify(versionRepository, never()).save(version);
        verify(packageRepository).findActiveByIdForUpdate(packageId);
        verify(versionRepository, never()).findActiveByIdAndPackageIdForUpdate(versionId, packageId);
    }

    @Test
    void rejectsBackwardPackageStatusTransition() {
        UUID packageId = UUID.randomUUID();
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setStatus(KnowledgePackage.Status.archived);
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);

        PackageService.PatchCommand command = new PackageService.PatchCommand(
                false, null, false, null, true, "active", false, null);

        assertThatThrownBy(() -> packageService.patch(packageId, command, actor, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verify(packageRepository, never()).save(pkg);
    }

    @Test
    void allowsDocumentedPackageStatusTransition() {
        UUID packageId = UUID.randomUUID();
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setStatus(KnowledgePackage.Status.active);
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);

        PackageService.PatchCommand command = new PackageService.PatchCommand(
                false, null, false, null, true, "deprecated", false, null);

        packageService.patch(packageId, command, actor, "127.0.0.1");

        assertThat(pkg.getStatus()).isEqualTo(KnowledgePackage.Status.deprecated);
        verify(packageRepository).save(pkg);
        verify(searchIndexUpdates).refreshPackageAfterCommit(packageId);
    }

    @Test
    void refusesToMakeFailedVersionCurrent() {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);
        version.setParseStatus(PackageVersion.ParseStatus.failed);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(versionRepository.findActiveByIdAndPackageIdForUpdate(versionId, packageId))
                .thenReturn(Optional.of(version));

        assertThatThrownBy(() -> packageService.setCurrentVersion(
                packageId, versionId, actor, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(pkg.getCurrentVersionId()).isNull();
        verify(packageRepository, never()).save(pkg);
    }

    @Test
    void switchesToSuccessfulVersionAndRefreshesSearch() {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AppUser actor = new AppUser();
        actor.setId(UUID.randomUUID());
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);
        version.setParseStatus(PackageVersion.ParseStatus.success);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(versionRepository.findActiveByIdAndPackageIdForUpdate(versionId, packageId))
                .thenReturn(Optional.of(version));

        packageService.setCurrentVersion(packageId, versionId, actor, "127.0.0.1");

        assertThat(pkg.getCurrentVersionId()).isEqualTo(versionId);
        verify(packageRepository).save(pkg);
        verify(searchIndexUpdates).refreshPackageAfterCommit(packageId);
    }

    @Test
    void replacesUploadMetadataAndTaxonomy() {
        UUID packageId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID oldTagId = UUID.randomUUID();
        UUID newTagId = UUID.randomUUID();
        UUID oldCollectionId = UUID.randomUUID();
        UUID newCollectionId = UUID.randomUUID();
        AppUser actor = new AppUser();
        actor.setId(ownerId);
        actor.setRole(AppUser.Role.editor);
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setOwnerId(ownerId);
        pkg.setTitle("Old");
        Tag newTag = new Tag();
        newTag.setId(newTagId);
        newTag.setName("new");
        CollectionEntity newCollection = new CollectionEntity();
        newCollection.setId(newCollectionId);
        when(accessService.requireWritable(packageId, actor)).thenReturn(pkg);
        when(packageRepository.findActiveByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(tagRepository.findAllByNameIn(java.util.Set.of("new"))).thenReturn(List.of(newTag));
        when(packageTagRepository.findAllByIdPackageId(packageId)).thenReturn(List.of(
                new PackageTag(new PackageTagId(packageId, oldTagId))));
        when(collectionRepository.findAllById(java.util.Set.of(newCollectionId)))
                .thenReturn(List.of(newCollection));
        when(packageCollectionRepository.findAllByIdPackageId(packageId)).thenReturn(List.of(
                new PackageCollection(new PackageCollectionId(packageId, oldCollectionId))));

        packageService.replaceUploadMetadata(
                packageId,
                "New",
                null,
                KnowledgePackage.SourceType.ai,
                " AI source ",
                List.of("new"),
                List.of(newCollectionId),
                actor,
                "127.0.0.1"
        );

        assertThat(pkg.getTitle()).isEqualTo("New");
        assertThat(pkg.getDescription()).isNull();
        assertThat(pkg.getSourceType()).isEqualTo(KnowledgePackage.SourceType.ai);
        assertThat(pkg.getSourceName()).isEqualTo("AI source");
        verify(packageTagRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(links ->
                java.util.stream.StreamSupport.stream(links.spliterator(), false)
                        .anyMatch(link -> link.getId().getTagId().equals(oldTagId))));
        verify(packageTagRepository).saveAll(org.mockito.ArgumentMatchers.argThat(links ->
                java.util.stream.StreamSupport.stream(links.spliterator(), false)
                        .anyMatch(link -> link.getId().getTagId().equals(newTagId))));
        verify(packageCollectionRepository).deleteAll(org.mockito.ArgumentMatchers.argThat(links ->
                java.util.stream.StreamSupport.stream(links.spliterator(), false)
                        .anyMatch(link -> link.getId().getCollectionId().equals(oldCollectionId))));
        verify(packageCollectionRepository).saveAll(org.mockito.ArgumentMatchers.argThat(links ->
                java.util.stream.StreamSupport.stream(links.spliterator(), false)
                        .anyMatch(link -> link.getId().getCollectionId().equals(newCollectionId))));
        verify(searchIndexUpdates).refreshPackageAfterCommit(packageId);
    }
}
