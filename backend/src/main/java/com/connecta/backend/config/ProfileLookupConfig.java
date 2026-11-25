package com.connecta.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The bounded executor of the customer profile lookup. A full queue drops the task (logged and counted); the
 * next message retries. On shutdown queued lookups finish within {@code shutdown-timeout}.
 */
@Configuration
@EnableAsync
public class ProfileLookupConfig {

    public static final String EXECUTOR = "profileLookupExecutor";
    public static final String DROPPED_METRIC = "meta.profile.lookup.dropped";

    private static final Logger log = LoggerFactory.getLogger(ProfileLookupConfig.class);

    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor profileLookupExecutor(ProfileLookupProperties properties, MeterRegistry meters) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("profile-lookup-");
        executor.setCorePoolSize(properties.poolSize());
        executor.setMaxPoolSize(properties.poolSize());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.setRejectedExecutionHandler(droppedLookupHandler(properties.queueCapacity(), meters));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds((int) properties.shutdownTimeout().toSeconds());
        return executor;
    }

    /** Drops the lookup without throwing into the publisher; the customer is looked up on the next message. */
    static RejectedExecutionHandler droppedLookupHandler(int queueCapacity, MeterRegistry meters) {
        Counter dropped = Counter.builder(DROPPED_METRIC)
                .description("Customer profile lookups dropped because the queue was full")
                .register(meters);
        return (task, pool) -> {
            dropped.increment();
            log.info("Customer profile lookup dropped, queue full (capacity {})", queueCapacity);
        };
    }
}
