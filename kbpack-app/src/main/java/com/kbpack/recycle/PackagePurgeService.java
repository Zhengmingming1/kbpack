package com.kbpack.recycle;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.id.UuidV7Generator;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PackagePurgeService {

    private final KnowledgePackageRepository packageRepository;
    private final PackageVersionRepository versionRepository;
    private final PackageAssetRepository assetRepository;
    private final ObjectStorageService storage;
    private final PurgeCleanupJobRepository cleanupJobRepository;
    private final PurgeCleanupCoordinator cleanupCoordinator;

    public PackagePurgeService(
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PackageAssetRepository assetRepository,
            ObjectStorageService storage,
            PurgeCleanupJobRepository cleanupJobRepository,
            PurgeCleanupCoordinator cleanupCoordinator
    ) {
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.cleanupJobRepository = cleanupJobRepository;
        this.cleanupCoordinator = cleanupCoordinator;
    }

    @Transactional
    public KnowledgePackage purge(UUID packageId) {
        KnowledgePackage pkg = packageRepository.findDeletedByIdForUpdate(packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        String externalPackageId = IdPrefix.PACKAGE.format(packageId);
        List<String> versionIds = new ArrayList<>();
        List<Map<String, String>> storageObjects = new ArrayList<>();
        for (PackageVersion version : versionRepository.findAllIncludingDeletedByPackageId(packageId)) {
            String externalVersionId = IdPrefix.VERSION.format(version.getId());
            versionIds.add(version.getId().toString());
            for (PackageAsset asset : assetRepository.findByVersionIdOrderByPathAsc(version.getId())) {
                storageObjects.add(storageObject(
                        storage.packagesBucket(),
                        externalPackageId + "/" + externalVersionId + "/files/" + asset.getPath()
                ));
            }
            if (version.getStoragePath() != null && !"pending".equals(version.getStoragePath())) {
                storageObjects.add(storageObject(storage.originalBucket(), version.getStoragePath()));
            }
        }

        PurgeCleanupJob cleanupJob = new PurgeCleanupJob(
                UuidV7Generator.generate(), packageId, versionIds, storageObjects, Instant.now()
        );
        cleanupJobRepository.saveAndFlush(cleanupJob);
        cleanupCoordinator.processAfterCommit(cleanupJob.getId());

        // Break the package-to-current-version reference before cascading version deletion.
        pkg.setCurrentVersionId(null);
        packageRepository.saveAndFlush(pkg);
        packageRepository.delete(pkg);
        packageRepository.flush();
        return pkg;
    }

    private Map<String, String> storageObject(String bucket, String key) {
        Map<String, String> object = new LinkedHashMap<>();
        object.put("bucket", bucket);
        object.put("key", key);
        return object;
    }
}
