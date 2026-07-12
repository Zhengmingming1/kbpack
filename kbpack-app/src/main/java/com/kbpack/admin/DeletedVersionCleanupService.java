package com.kbpack.admin;

import com.kbpack.common.id.IdPrefix;
import com.kbpack.common.storage.ObjectStorageService;
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
public class DeletedVersionCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DeletedVersionCleanupService.class);
    private final CleanupVersionRepository versionRepository;
    private final CleanupPackageRepository packageRepository;
    private final PackageAssetRepository assetRepository;
    private final ObjectStorageService storage;
    private final SearchIndexService searchIndexService;
    private final TransactionTemplate transactionTemplate;
    private final RuntimeSettingService runtimeSettings;

    public DeletedVersionCleanupService(
            CleanupVersionRepository versionRepository,
            CleanupPackageRepository packageRepository,
            PackageAssetRepository assetRepository,
            ObjectStorageService storage,
            SearchIndexService searchIndexService,
            TransactionTemplate transactionTemplate,
            RuntimeSettingService runtimeSettings
    ) {
        this.versionRepository = versionRepository;
        this.packageRepository = packageRepository;
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.searchIndexService = searchIndexService;
        this.transactionTemplate = transactionTemplate;
        this.runtimeSettings = runtimeSettings;
    }

    @Scheduled(cron = "${kbpack.cleanup.version-cron:0 40 3 * * *}")
    public void scheduledCleanup() {
        cleanupBefore(Instant.now().minus(runtimeSettings.cleanupRetentionDays(), ChronoUnit.DAYS));
    }

    public int cleanupBefore(Instant cutoff) {
        int cleaned = 0;
        for (var version : versionRepository.findByDeletedAtBefore(cutoff)) {
            var pkg = packageRepository.findById(version.getPackageId()).orElse(null);
            if (pkg == null || pkg.getDeletedAt() != null) continue;
            try {
                String packageId = IdPrefix.PACKAGE.format(version.getPackageId());
                String versionId = IdPrefix.VERSION.format(version.getId());
                for (PackageAsset asset : assetRepository.findByVersionIdOrderByPathAsc(version.getId())) {
                    storage.remove(storage.packagesBucket(),
                            packageId + "/" + versionId + "/files/" + asset.getPath());
                }
                if (version.getStoragePath() != null && !"pending".equals(version.getStoragePath())) {
                    storage.remove(storage.originalBucket(), version.getStoragePath());
                }
                searchIndexService.deleteVersion(version.getId());
                transactionTemplate.executeWithoutResult(status -> versionRepository.deleteById(version.getId()));
                cleaned++;
            } catch (Exception error) {
                log.error("Failed to physically clean version {}", version.getId(), error);
            }
        }
        return cleaned;
    }
}
