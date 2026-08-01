package com.kbpack.recycle;

import com.kbpack.admin.OperationLogService;
import com.kbpack.admin.RuntimeSettingService;
import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.pkg.KnowledgePackage;
import com.kbpack.pkg.KnowledgePackageRepository;
import com.kbpack.pkg.PackageAccessService;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import com.kbpack.search.SearchIndexUpdateCoordinator;
import com.kbpack.user.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
public class RecycleBinService {

    public record DeletedPackage(
            KnowledgePackage pkg,
            Instant purgeAt,
            boolean canRestore,
            long versionsCount
    ) {
    }

    private final KnowledgePackageRepository packageRepository;
    private final PackageVersionRepository versionRepository;
    private final PackageAccessService accessService;
    private final RuntimeSettingService runtimeSettings;
    private final SearchIndexUpdateCoordinator indexCoordinator;
    private final OperationLogService operationLogService;
    private final PackagePurgeService purgeService;
    private final Clock clock;

    @Autowired
    public RecycleBinService(
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PackageAccessService accessService,
            RuntimeSettingService runtimeSettings,
            SearchIndexUpdateCoordinator indexCoordinator,
            OperationLogService operationLogService,
            PackagePurgeService purgeService
    ) {
        this(packageRepository, versionRepository, accessService, runtimeSettings, indexCoordinator,
                operationLogService, purgeService, Clock.systemUTC());
    }

    RecycleBinService(
            KnowledgePackageRepository packageRepository,
            PackageVersionRepository versionRepository,
            PackageAccessService accessService,
            RuntimeSettingService runtimeSettings,
            SearchIndexUpdateCoordinator indexCoordinator,
            OperationLogService operationLogService,
            PackagePurgeService purgeService,
            Clock clock
    ) {
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.accessService = accessService;
        this.runtimeSettings = runtimeSettings;
        this.indexCoordinator = indexCoordinator;
        this.operationLogService = operationLogService;
        this.purgeService = purgeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DeletedPackage> list(AppUser user, Pageable pageable) {
        accessService.requireContentWriter(user);
        int retentionDays = runtimeSettings.cleanupRetentionDays();
        Instant now = clock.instant();
        return packageRepository.findDeletedVisibleTo(
                user.getId(), accessService.isAdministrator(user), pageable
        ).map(pkg -> {
            Instant purgeAt = pkg.getDeletedAt().plus(retentionDays, ChronoUnit.DAYS);
            return new DeletedPackage(
                    pkg,
                    purgeAt,
                    now.isBefore(purgeAt),
                    versionRepository.countByPackageId(pkg.getId())
            );
        });
    }

    @Transactional
    public KnowledgePackage restore(UUID packageId, AppUser user, String ip) {
        accessService.requireContentWriter(user);
        KnowledgePackage pkg = packageRepository.findDeletedByIdForUpdate(packageId)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        requireManageable(pkg, user);

        int retentionDays = runtimeSettings.cleanupRetentionDays();
        Instant purgeAt = pkg.getDeletedAt().plus(retentionDays, ChronoUnit.DAYS);
        if (!clock.instant().isBefore(purgeAt)) {
            throw new ApiException(ErrorCode.CONFLICT, "Restore period has expired");
        }

        Instant deletedAt = pkg.getDeletedAt();
        for (PackageVersion version : versionRepository.findByPackageIdAndDeletedAt(packageId, deletedAt)) {
            version.setDeletedAt(null);
        }
        pkg.setDeletedAt(null);
        packageRepository.save(pkg);
        indexCoordinator.refreshPackageAfterCommit(packageId);
        operationLogService.record(
                user.getId(),
                "package.restore",
                "knowledge_package",
                packageId,
                Map.of("deleted_at", deletedAt.toString()),
                ip
        );
        return pkg;
    }

    public void purge(UUID packageId, AppUser user, String ip) {
        accessService.requireContentWriter(user);
        KnowledgePackage pkg = packageRepository.findById(packageId)
                .filter(candidate -> candidate.getDeletedAt() != null)
                .orElseThrow(() -> new ApiException(ErrorCode.PACKAGE_NOT_FOUND));
        requireManageable(pkg, user);
        Instant deletedAt = pkg.getDeletedAt();
        purgeService.purge(packageId);
        operationLogService.record(
                user.getId(),
                "package.purge",
                "knowledge_package",
                packageId,
                Map.of("deleted_at", deletedAt.toString()),
                ip
        );
    }

    private void requireManageable(KnowledgePackage pkg, AppUser user) {
        if (accessService.isAdministrator(user)) {
            return;
        }
        if (user.getRole() == AppUser.Role.editor && user.getId().equals(pkg.getOwnerId())) {
            return;
        }
        throw new ApiException(ErrorCode.PACKAGE_NOT_FOUND);
    }
}
