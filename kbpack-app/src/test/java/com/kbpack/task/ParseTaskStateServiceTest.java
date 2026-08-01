package com.kbpack.task;

import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParseTaskStateServiceTest {

    @Test
    void manualRetryStartsANewAttemptRoundAfterAutomaticRetriesAreExhausted() {
        ParseTaskRepository taskRepository = mock(ParseTaskRepository.class);
        PackageVersionRepository versionRepository = mock(PackageVersionRepository.class);
        ParseTaskStateService service = new ParseTaskStateService(taskRepository, versionRepository);
        UUID taskId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ParseTask task = new ParseTask();
        task.setVersionId(versionId);
        task.setStatus(ParseTask.Status.failed);
        task.setAttemptCount(3);
        task.setMaxAttempts(3);
        task.setErrorMessage("failed");
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        version.setParseStatus(PackageVersion.ParseStatus.failed);
        version.setParseError("failed");
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(versionRepository.findActiveById(versionId)).thenReturn(Optional.of(version));

        ParseTask retried = service.retry(taskId);

        assertThat(retried.getStatus()).isEqualTo(ParseTask.Status.pending);
        assertThat(retried.getAttemptCount()).isZero();
        assertThat(retried.getErrorMessage()).isNull();
        assertThat(version.getParseStatus()).isEqualTo(PackageVersion.ParseStatus.pending);
        assertThat(version.getParseError()).isNull();
        verify(taskRepository).save(task);
        verify(versionRepository).save(version);
    }

    @Test
    void automaticRetryKeepsVersionInPendingState() {
        ParseTaskRepository taskRepository = mock(ParseTaskRepository.class);
        PackageVersionRepository versionRepository = mock(PackageVersionRepository.class);
        ParseTaskStateService service = new ParseTaskStateService(taskRepository, versionRepository);
        UUID taskId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        ParseTask task = new ParseTask();
        task.setVersionId(versionId);
        task.setStatus(ParseTask.Status.processing);
        task.setAttemptCount(1);
        task.setMaxAttempts(3);
        PackageVersion version = new PackageVersion();
        version.setId(versionId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(versionRepository.findActiveById(versionId)).thenReturn(Optional.of(version));

        service.fail(taskId, new IllegalStateException("temporary"));

        assertThat(task.getStatus()).isEqualTo(ParseTask.Status.retry_scheduled);
        assertThat(version.getParseStatus()).isEqualTo(PackageVersion.ParseStatus.pending);
        assertThat(version.getParseError()).isEqualTo("temporary");
    }
}
