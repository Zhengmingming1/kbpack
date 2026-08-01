package com.kbpack.admin;

import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.recycle.PackagePurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class DeletedPackageCleanupService {
    private static final Logger log = LoggerFactory.getLogger(DeletedPackageCleanupService.class);
    private final CleanupPackageRepository packageRepository;
    private final PackagePurgeService purgeService;
    private final RuntimeSettingService runtimeSettings;

    public DeletedPackageCleanupService(
            CleanupPackageRepository packageRepository,
            PackagePurgeService purgeService,
            RuntimeSettingService runtimeSettings
    ) {
        this.packageRepository = packageRepository;
        this.purgeService = purgeService;
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
                purgeService.purge(pkg.getId());
                cleaned++;
            } catch (Exception error) {
                log.error("Failed to physically clean package {}", pkg.getId(), error);
            }
        }
        return cleaned;
    }
}
