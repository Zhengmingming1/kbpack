package com.kbpack.task;

import com.kbpack.admin.RuntimeSettingService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ParseTaskScheduler implements SchedulingConfigurer {
    private final ParseTaskStateService stateService;
    private final ParseTaskWorker worker;
    private final ThreadPoolTaskExecutor executor;
    private final RuntimeSettingService runtimeSettings;
    private volatile boolean ready;

    public ParseTaskScheduler(
            ParseTaskStateService stateService,
            ParseTaskWorker worker,
            @Qualifier("parseTaskExecutor") ThreadPoolTaskExecutor executor,
            RuntimeSettingService runtimeSettings
    ) {
        this.stateService = stateService;
        this.worker = worker;
        this.executor = executor;
        this.runtimeSettings = runtimeSettings;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.addTriggerTask(this::poll, context -> {
            Instant completed = context.lastCompletion();
            if (completed == null) completed = Instant.now();
            return completed.plusSeconds(runtimeSettings.taskPollIntervalSeconds());
        });
    }

    public void poll() {
        if (!ready) return;
        int configuredSize = Math.max(1, runtimeSettings.taskThreadPoolSize());
        resizeExecutor(configuredSize);
        int runningCapacity = Math.max(0, executor.getMaxPoolSize() - executor.getActiveCount());
        int queueCapacity = executor.getThreadPoolExecutor().getQueue().remainingCapacity();
        int available = runningCapacity + queueCapacity;
        int limit = Math.min(configuredSize, available);
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

    private void resizeExecutor(int size) {
        int currentMax = executor.getMaxPoolSize();
        if (size > currentMax) {
            executor.setMaxPoolSize(size);
            executor.setCorePoolSize(size);
        } else if (size < currentMax) {
            executor.setCorePoolSize(size);
            executor.setMaxPoolSize(size);
        }
    }
}
