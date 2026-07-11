package com.kbpack.pkg;

import com.kbpack.admin.OperationLogService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.user.AppUser;
import com.kbpack.search.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
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
    @Mock private TagRepository tagRepository;
    @Mock private PackageTagRepository packageTagRepository;
    @Mock private CollectionRepository collectionRepository;
    @Mock private PackageCollectionRepository packageCollectionRepository;
    @Mock private FavoriteRepository favoriteRepository;
    @Mock private PackageAccessService accessService;
    @Mock private OperationLogService operationLogService;
    @Mock private SearchIndexService searchIndexService;

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
        when(versionRepository.findActiveByIdAndPackageId(versionId, packageId))
                .thenReturn(Optional.of(version));

        assertThatThrownBy(() -> packageService.deleteVersion(packageId, versionId, actor, "127.0.0.1"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.CURRENT_VERSION_DELETE_FORBIDDEN));
        verify(versionRepository, never()).save(version);
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
    }
}
