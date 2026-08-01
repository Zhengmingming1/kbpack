package com.kbpack.recycle;

import com.kbpack.common.storage.ObjectStorageService;
import com.kbpack.search.SearchIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PurgeCleanupCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Mock private PurgeCleanupJobRepository jobRepository;
    @Mock private ObjectStorageService storage;
    @Mock private SearchIndexService searchIndexService;
    @Mock private TransactionTemplate transactionTemplate;

    @Test
    void completesIdempotentExternalCleanup() {
        UUID versionId = UUID.randomUUID();
        PurgeCleanupJob job = job(versionId);
        PurgeCleanupCoordinator coordinator = coordinator();

        coordinator.processClaimed(job);

        verify(storage).remove("packages", "pkg/ver/files/index.html");
        verify(searchIndexService).deleteVersion(versionId);
        assertThat(job.getStatus()).isEqualTo(PurgeCleanupJob.Status.completed);
        assertThat(job.getCompletedAt()).isEqualTo(NOW);
        assertThat(job.getLastError()).isNull();
    }

    @Test
    void failedExternalCleanupRemainsDurablyRetryable() {
        UUID versionId = UUID.randomUUID();
        PurgeCleanupJob job = job(versionId);
        PurgeCleanupCoordinator coordinator = coordinator();
        doThrow(new ObjectStorageService.StorageException("storage unavailable", new RuntimeException()))
                .when(storage).remove("packages", "pkg/ver/files/index.html");

        coordinator.processClaimed(job);

        assertThat(job.getStatus()).isEqualTo(PurgeCleanupJob.Status.retry);
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(job.getLastError()).contains("storage unavailable");
    }

    private PurgeCleanupCoordinator coordinator() {
        return new PurgeCleanupCoordinator(
                jobRepository,
                storage,
                searchIndexService,
                transactionTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private PurgeCleanupJob job(UUID versionId) {
        return new PurgeCleanupJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of(versionId.toString()),
                List.of(Map.of("bucket", "packages", "key", "pkg/ver/files/index.html")),
                NOW.minusSeconds(1)
        );
    }
}
