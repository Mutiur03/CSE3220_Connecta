package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The customer profile lookup against Meta (app.meta.profile-lookup.*): the HTTP timeout and the bounded
 * executor it runs on (shutdown-timeout: how long queued lookups may finish on shutdown). picture-ttl: a stored
 * profile is looked up again once it is this old (Meta picture URLs expire). import-refresh-limit: most customers
 * looked up after one history import (0 turns that off). When the queue is full the lookup is dropped and retried on the next inbound message.
 */
@ConfigurationProperties(prefix = "app.meta.profile-lookup")
public record ProfileLookupProperties(Duration timeout, Integer poolSize, Integer queueCapacity,
        Duration shutdownTimeout, Duration pictureTtl, Integer importRefreshLimit) {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);
    private static final int DEFAULT_POOL_SIZE = 2;
    private static final int DEFAULT_QUEUE_CAPACITY = 100;
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration DEFAULT_PICTURE_TTL = Duration.ofHours(12);
    private static final int DEFAULT_IMPORT_REFRESH_LIMIT = 20;

    public ProfileLookupProperties {
        pictureTtl = pictureTtl == null || pictureTtl.isNegative() || pictureTtl.isZero() ? DEFAULT_PICTURE_TTL
                : pictureTtl;
        importRefreshLimit = importRefreshLimit == null || importRefreshLimit < 0 ? DEFAULT_IMPORT_REFRESH_LIMIT
                : importRefreshLimit;
        timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        poolSize = poolSize == null ? DEFAULT_POOL_SIZE : poolSize;
        queueCapacity = queueCapacity == null ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
        shutdownTimeout = shutdownTimeout == null ? DEFAULT_SHUTDOWN_TIMEOUT : shutdownTimeout;
    }
}
