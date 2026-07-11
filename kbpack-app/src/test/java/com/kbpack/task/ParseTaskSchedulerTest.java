package com.kbpack.task;

import com.kbpack.common.config.KbpackProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParseTaskSchedulerTest {

    @Mock
    private ParseTaskStateService stateService;
    @Mock
    private ParseTaskWorker worker;
    @Mock
    private ThreadPoolTaskExecutor executor;
    @Mock
    private ThreadPoolExecutor threadPoolExecutor;
    @Mock
    private BlockingQueue<Runnable> queue;

    private ParseTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        KbpackProperties properties = new KbpackProperties();
        properties.getTask().setThreadPoolSize(1);
        scheduler = new ParseTaskScheduler(stateService, worker, executor, properties);
    }

    @Test
    void doesNotPollBeforeApplicationIsReady() {
        scheduler.poll();

        verifyNoInteractions(stateService, worker, executor);
    }

    @Test
    void recoversInterruptedTasksBeforePollingAndDispatchingWork() {
        UUID taskId = UUID.randomUUID();
        when(executor.getMaxPoolSize()).thenReturn(1);
        when(executor.getActiveCount()).thenReturn(0);
        when(executor.getThreadPoolExecutor()).thenReturn(threadPoolExecutor);
        when(threadPoolExecutor.getQueue()).thenReturn(queue);
        when(queue.remainingCapacity()).thenReturn(0);
        when(stateService.claim(1)).thenReturn(List.of(taskId));
        doAnswer(invocation -> {
            scheduler.poll();
            return null;
        }).when(stateService).recoverInterrupted();
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        scheduler.recoverInterruptedTasks();
        scheduler.poll();

        InOrder recoveryBeforeClaim = inOrder(stateService);
        recoveryBeforeClaim.verify(stateService).recoverInterrupted();
        recoveryBeforeClaim.verify(stateService).claim(1);
        verify(executor).execute(any(Runnable.class));
        verify(worker).process(taskId);
        verify(stateService, never()).releaseClaim(taskId);
    }
}
