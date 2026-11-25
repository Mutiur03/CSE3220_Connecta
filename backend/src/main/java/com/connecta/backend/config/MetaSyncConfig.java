package com.connecta.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** One worker for the recent-message sync; a run that finds it busy is dropped, the next interval retries. */
@Configuration
public class MetaSyncConfig {

    public static final String EXECUTOR = "metaSyncExecutor";

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor metaSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("meta-sync-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS);
        return executor;
    }
}
