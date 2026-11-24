package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The customer profile lookup against Meta (app.meta.profile-lookup.*): the HTTP timeout and the bounded
 * executor it runs on. When the queue is full the lookup is dropped and retried on the next inbound message.
 */
@ConfigurationProperties(prefix = "app.meta.profile-lookup")
public record ProfileLookupProperties(Duration timeout, Integer poolSize, Integer queueCapacity) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_POOL_SIZE = 2;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;

    public ProfileLookupProperties {
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        poolSize = poolSize == null ? DEFAULT_POOL_SIZE : poolSize;
        queueCapacity = queueCapacity == null ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
    }
}
