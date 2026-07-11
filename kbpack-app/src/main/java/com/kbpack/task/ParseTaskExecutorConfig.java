package com.kbpack.task;

import com.kbpack.common.config.KbpackProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ParseTaskExecutorConfig {
    @Bean("parseTaskExecutor")
    ThreadPoolTaskExecutor parseTaskExecutor(KbpackProperties properties) {
        int size = Math.max(1, properties.getTask().getThreadPoolSize());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(size * 2);
        executor.setThreadNamePrefix("kbpack-parse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
