package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The customer profile lookup against Meta (app.meta.profile-lookup.*): the HTTP timeout and the bounded
 * executor it runs on (shutdown-timeout: how long queued lookups may finish on shutdown). When the queue is full the lookup is dropped and retried on the next inbound message.
 */
@ConfigurationProperties(prefix = "app.meta.profile-lookup")
public record ProfileLookupProperties(Duration timeout, Integer poolSize, Integer queueCapacity,
        Duration shutdownTimeout) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_POOL_SIZE = 2;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    public ProfileLookupProperties {
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        poolSize = poolSize == null ? DEFAULT_POOL_SIZE : poolSize;
        queueCapacity = queueCapacity == null ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
        shutdownTimeout = shutdownTimeout == null ? DEFAULT_SHUTDOWN_TIMEOUT : shutdownTimeout;
    }
}
