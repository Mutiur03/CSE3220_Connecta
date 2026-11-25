package com.connecta.backend.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.RejectedExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The bounded executor that copies incoming media, so a webhook or the scheduler never waits for a download.
 * A full queue drops the task (logged and counted); the retry sweep picks the attachment up later.
 */
@Configuration
public class AttachmentMirrorConfig {

    public static final String EXECUTOR = "attachmentMirrorExecutor";
    public static final String DROPPED_METRIC = "attachments.mirror.dropped";

    private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final Logger log = LoggerFactory.getLogger(AttachmentMirrorConfig.class);

    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor attachmentMirrorExecutor(AttachmentMirrorProperties properties, MeterRegistry meters) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("attachment-mirror-");
        executor.setCorePoolSize(properties.mirrorPoolSize());
        executor.setMaxPoolSize(properties.mirrorPoolSize());
        executor.setQueueCapacity(properties.mirrorQueueCapacity());
        executor.setRejectedExecutionHandler(droppedCopyHandler(properties.mirrorQueueCapacity(), meters));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_TIMEOUT_SECONDS);
        return executor;
    }

    static RejectedExecutionHandler droppedCopyHandler(int queueCapacity, MeterRegistry meters) {
        Counter dropped = Counter.builder(DROPPED_METRIC)
                .description("Attachment copies dropped because the queue was full")
                .register(meters);
        return (task, pool) -> {
            dropped.increment();
            log.info("Attachment copy dropped, queue full (capacity {}); the sweep retries it", queueCapacity);
        };
    }
}
