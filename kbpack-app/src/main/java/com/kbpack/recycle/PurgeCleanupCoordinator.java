package com.kbpack.recycle;

import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.search.SearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PurgeCleanupCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PurgeCleanupCoordinator.class);
    private static final List<PurgeCleanupJob.Status> READY_STATUSES = List.of(
            PurgeCleanupJob.Status.pending,
            PurgeCleanupJob.Status.retry
    );

    private final PurgeCleanupJobRepository jobRepository;
    private final ObjectStorageService storage;
    private final SearchIndexService searchIndexService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Autowired
    public PurgeCleanupCoordinator(
            PurgeCleanupJobRepository jobRepository,
            ObjectStorageService storage,
            SearchIndexService searchIndexService,
            PlatformTransactionManager transactionManager
    ) {
        this(jobRepository, storage, searchIndexService, requiresNew(transactionManager), Clock.systemUTC());
    }

    PurgeCleanupCoordinator(
            PurgeCleanupJobRepository jobRepository,
            ObjectStorageService storage,
            SearchIndexService searchIndexService,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.storage = storage;
        this.searchIndexService = searchIndexService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public void processAfterCommit(UUID jobId) {
        Runnable action = () -> process(jobId);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Scheduled(
            fixedDelayString = "${kbpack.cleanup.purge-job-delay-ms:60000}",
            initialDelayString = "${kbpack.cleanup.purge-job-initial-delay-ms:30000}"
    )
    public void retryPending() {
        List<UUID> jobIds = jobRepository
                .findTop20ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        READY_STATUSES, clock.instant()
                )
                .stream()
                .map(PurgeCleanupJob::getId)
                .toList();
        jobIds.forEach(this::process);
    }

    public void process(UUID jobId) {
        try {
            transactionTemplate.executeWithoutResult(ignored -> jobRepository.findByIdForUpdate(jobId)
                    .filter(job -> job.getStatus() != PurgeCleanupJob.Status.completed)
                    .filter(job -> !job.getNextAttemptAt().isAfter(clock.instant()))
                    .ifPresent(this::processClaimed));
        } catch (RuntimeException error) {
            // The package transaction is already committed. Keep the pending row for the scheduler.
            log.error("Failed to start purge cleanup job {}; retry remains pending", jobId, error);
        }
    }

    void processClaimed(PurgeCleanupJob job) {
        try {
            for (Map<String, String> object : job.getStorageObjects()) {
                storage.remove(object.get("bucket"), object.get("key"));
            }
            for (String versionId : job.getVersionIds()) {
                searchIndexService.deleteVersion(UUID.fromString(versionId));
            }
            job.markCompleted(clock.instant());
        } catch (RuntimeException error) {
            job.markRetry(clock.instant(), error);
            log.error("Failed purge cleanup job {} for package {}; retry scheduled",
                    job.getId(), job.getPackageId(), error);
        }
    }

    private static TransactionTemplate requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
