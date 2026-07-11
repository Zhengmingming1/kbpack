package com.kbpack.task;

import com.kbpack.common.config.KbpackProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

@Component
public class ParseTaskScheduler {
    private final ParseTaskStateService stateService;
    private final ParseTaskWorker worker;
    private final ThreadPoolTaskExecutor executor;
    private final KbpackProperties properties;
    private volatile boolean ready;

    public ParseTaskScheduler(
            ParseTaskStateService stateService,
            ParseTaskWorker worker,
            @Qualifier("parseTaskExecutor") ThreadPoolTaskExecutor executor,
            KbpackProperties properties
    ) {
        this.stateService = stateService;
        this.worker = worker;
        this.executor = executor;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${kbpack.task.poll-interval-ms:3000}")
    public void poll() {
        if (!ready) return;
        int runningCapacity = Math.max(0, executor.getMaxPoolSize() - executor.getActiveCount());
        int queueCapacity = executor.getThreadPoolExecutor().getQueue().remainingCapacity();
        int available = runningCapacity + queueCapacity;
        int limit = Math.min(Math.max(1, properties.getTask().getThreadPoolSize()), available);
        if (limit <= 0) return;
        stateService.claim(limit).forEach(taskId -> {
            try {
                executor.execute(() -> worker.process(taskId));
            } catch (TaskRejectedException error) {
                stateService.releaseClaim(taskId);
            }
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedTasks() {
        stateService.recoverInterrupted();
        ready = true;
    }
}
