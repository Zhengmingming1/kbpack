package com.kbpack.task;

import com.kbpack.common.error.ApiException;
import com.kbpack.common.error.ErrorCode;
import com.kbpack.pkg.PackageVersion;
import com.kbpack.pkg.PackageVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ParseTaskStateService {
    private final ParseTaskRepository taskRepository;
    private final PackageVersionRepository versionRepository;

    public ParseTaskStateService(ParseTaskRepository taskRepository, PackageVersionRepository versionRepository) {
        this.taskRepository = taskRepository;
        this.versionRepository = versionRepository;
    }

    @Transactional
    public List<UUID> claim(int limit) {
        List<ParseTask> tasks = taskRepository.findRunnable(Instant.now(), limit);
        Instant now = Instant.now();
        for (ParseTask task : tasks) {
            task.setStatus(ParseTask.Status.processing);
            task.setStartedAt(now);
            task.setFinishedAt(null);
            task.setErrorMessage(null);
            task.setAttemptCount(task.getAttemptCount() + 1);
        }
        taskRepository.saveAll(tasks);
        return tasks.stream().map(ParseTask::getId).toList();
    }

    @Transactional
    public void releaseClaim(UUID taskId) {
        ParseTask task = require(taskId);
        if (task.getStatus() != ParseTask.Status.processing) return;
        task.setStatus(ParseTask.Status.pending);
        task.setStartedAt(null);
        task.setAttemptCount(Math.max(0, task.getAttemptCount() - 1));
        taskRepository.save(task);
    }

    @Transactional
    public int recoverInterrupted() {
        List<ParseTask> tasks = taskRepository.findAllByStatus(ParseTask.Status.processing);
        for (ParseTask task : tasks) {
            task.setStatus(ParseTask.Status.pending);
            task.setStartedAt(null);
            task.setNextRetryAt(null);
            task.setErrorMessage("应用重启后重新调度");
            versionRepository.findActiveById(task.getVersionId()).ifPresent(version -> {
                version.setParseStatus(PackageVersion.ParseStatus.pending);
                version.setParseError(null);
                versionRepository.save(version);
            });
        }
        taskRepository.saveAll(tasks);
        return tasks.size();
    }

    @Transactional
    public void complete(UUID taskId) {
        ParseTask task = require(taskId);
        task.setStatus(ParseTask.Status.success);
        task.setFinishedAt(Instant.now());
        task.setNextRetryAt(null);
        taskRepository.save(task);
    }

    @Transactional
    public void fail(UUID taskId, Throwable error) {
        ParseTask task = require(taskId);
        String message = rootMessage(error);
        task.setErrorMessage(message.length() > 4000 ? message.substring(0, 4000) : message);
        task.setFinishedAt(Instant.now());
        if (task.getAttemptCount() < task.getMaxAttempts()) {
            task.setStatus(ParseTask.Status.retry_scheduled);
            long delay = Math.min(300, 30L << Math.max(0, task.getAttemptCount() - 1));
            task.setNextRetryAt(Instant.now().plusSeconds(delay));
        } else {
            task.setStatus(ParseTask.Status.failed);
            task.setNextRetryAt(null);
        }
        taskRepository.save(task);
        versionRepository.findActiveById(task.getVersionId()).ifPresent(version -> {
            version.setParseStatus(PackageVersion.ParseStatus.failed);
            version.setParseError(task.getErrorMessage());
            versionRepository.save(version);
        });
    }

    @Transactional
    public ParseTask retry(UUID taskId) {
        ParseTask task = require(taskId);
        if (task.getAttemptCount() >= task.getMaxAttempts()) {
            throw new ApiException(ErrorCode.TASK_MAX_RETRIES);
        }
        if (task.getStatus() == ParseTask.Status.processing) {
            throw new ApiException(ErrorCode.CONFLICT, "任务正在执行");
        }
        task.setStatus(ParseTask.Status.pending);
        task.setNextRetryAt(null);
        task.setErrorMessage(null);
        task.setFinishedAt(null);
        taskRepository.save(task);
        versionRepository.findActiveById(task.getVersionId()).ifPresent(version -> {
            version.setParseStatus(PackageVersion.ParseStatus.pending);
            version.setParseError(null);
            versionRepository.save(version);
        });
        return task;
    }

    private ParseTask require(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "解析任务不存在"));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
