package com.kbpack.admin;

import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.PackageAsset;
import com.kbpack.pkg.PackageAssetRepository;
import com.kbpack.search.SearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class DeletedPackageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DeletedPackageCleanupService.class);
    private final CleanupPackageRepository packageRepository;
    private final CleanupVersionRepository versionRepository;
    private final PackageAssetRepository assetRepository;
    private final ObjectStorageService storage;
    private final SearchIndexService searchIndexService;
    private final TransactionTemplate transactionTemplate;
    private final RuntimeSettingService runtimeSettings;

    public DeletedPackageCleanupService(
            CleanupPackageRepository packageRepository,
            CleanupVersionRepository versionRepository,
            PackageAssetRepository assetRepository,
            ObjectStorageService storage,
            SearchIndexService searchIndexService,
            TransactionTemplate transactionTemplate,
            RuntimeSettingService runtimeSettings
    ) {
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.searchIndexService = searchIndexService;
        this.transactionTemplate = transactionTemplate;
        this.runtimeSettings = runtimeSettings;
    }

    @Scheduled(cron = "${kbpack.cleanup.cron:0 20 3 * * *}")
    public void scheduledCleanup() {
        cleanupBefore(Instant.now().minus(runtimeSettings.cleanupRetentionDays(), ChronoUnit.DAYS));
    }

    public int cleanupBefore(Instant cutoff) {
        int cleaned = 0;
        for (KnowledgePackage pkg : packageRepository.findByDeletedAtBefore(cutoff)) {
            try {
                String packageId = IdPrefix.PACKAGE.format(pkg.getId());
                var versions = versionRepository.findByPackageId(pkg.getId());
                for (var version : versions) {
                    String versionId = IdPrefix.VERSION.format(version.getId());
                    for (PackageAsset asset : assetRepository.findByVersionIdOrderByPathAsc(version.getId())) {
                        storage.remove(storage.packagesBucket(),
                                packageId + "/" + versionId + "/files/" + asset.getPath());
                    }
                    if (version.getStoragePath() != null && !"pending".equals(version.getStoragePath())) {
                        storage.remove(storage.originalBucket(), version.getStoragePath());
                    }
                    searchIndexService.deleteVersion(version.getId());
                }
                transactionTemplate.executeWithoutResult(status -> packageRepository.deleteById(pkg.getId()));
                cleaned++;
            } catch (Exception error) {
                log.error("Failed to physically clean package {}", pkg.getId(), error);
            }
        }
        return cleaned;
    }
}
