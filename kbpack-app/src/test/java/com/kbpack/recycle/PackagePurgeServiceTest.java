package com.kbpack.recycle;

import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class PackagePurgeServiceTest {

    @Mock private KnowledgePackageRepository packageRepository;
    @Mock private PackageVersionRepository versionRepository;
    @Mock private PackageAssetRepository assetRepository;
    @Mock private ObjectStorageService storage;
    @Mock private PurgeCleanupJobRepository cleanupJobRepository;
    @Mock private PurgeCleanupCoordinator cleanupCoordinator;

    @InjectMocks private PackagePurgeService service;

    @Test
    void persistsExternalCleanupBeforeDeletingDatabasePackage() {
        UUID packageId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        KnowledgePackage pkg = new KnowledgePackage();
        pkg.setId(packageId);
        pkg.setCurrentVersionId(versionId);
        pkg.setDeletedAt(Instant.now());
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setPackageId(packageId);
        version.setStoragePath("original/key.zip");
        PackageAsset asset = new PackageAsset();
        asset.setVersionId(versionId);
        asset.setPath("guide/index.md");
        when(packageRepository.findDeletedByIdForUpdate(packageId)).thenReturn(Optional.of(pkg));
        when(versionRepository.findAllIncludingDeletedByPackageId(packageId)).thenReturn(List.of(version));
        when(assetRepository.findByVersionIdOrderByPathAsc(versionId)).thenReturn(List.of(asset));
        when(storage.packagesBucket()).thenReturn("packages");
        when(storage.originalBucket()).thenReturn("original");

        service.purge(packageId);

        ArgumentCaptor<PurgeCleanupJob> cleanupJob = ArgumentCaptor.forClass(PurgeCleanupJob.class);
        verify(cleanupJobRepository).saveAndFlush(cleanupJob.capture());
        assertThat(cleanupJob.getValue().getPackageId()).isEqualTo(packageId);
        assertThat(cleanupJob.getValue().getVersionIds()).containsExactly(versionId.toString());
        assertThat(cleanupJob.getValue().getStorageObjects()).containsExactlyInAnyOrder(
                java.util.Map.of(
                        "bucket", "packages",
                        "key", IdPrefix.PACKAGE.format(packageId) + "/" + IdPrefix.VERSION.format(versionId)
                                + "/files/guide/index.md"
                ),
                java.util.Map.of("bucket", "original", "key", "original/key.zip")
        );
        verify(cleanupCoordinator).processAfterCommit(cleanupJob.getValue().getId());
        verify(storage, never()).remove(anyString(), anyString());
        assertThat(pkg.getCurrentVersionId()).isNull();
        var order = inOrder(packageRepository);
        order.verify(packageRepository).saveAndFlush(pkg);
        order.verify(packageRepository).delete(pkg);
        order.verify(packageRepository).flush();
    }
}
