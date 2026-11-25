package com.connecta.backend.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Copying incoming Meta media into stored_files (app.attachments.mirror-*). A missing or invalid value falls back
 * to its default.
 *
 * @param mirrorMaxBytes largest file copied; a bigger download is aborted (default 25 MB)
 * @param mirrorAllowedHostSuffixes the only hosts media is downloaded from (a host equal to or under a suffix)
 * @param mirrorMaxAge attachments older than this are no longer retried (their Meta link has likely expired)
 * @param mirrorRetryAfter a failed or unfinished copy is tried again after this long
 * @param mirrorBatchSize most attachments one sweep or one history import copies
 * @param mirrorTimeout connect and read timeout of each download request
 * @param mirrorPoolSize threads of the copy executor
 * @param mirrorQueueCapacity queued copy tasks; more are dropped and left to the sweep
 */
@ConfigurationProperties(prefix = "app.attachments")
public record AttachmentMirrorProperties(Long mirrorMaxBytes, List<String> mirrorAllowedHostSuffixes,
        Duration mirrorMaxAge, Duration mirrorRetryAfter, Integer mirrorBatchSize, Duration mirrorTimeout,
        Integer mirrorPoolSize, Integer mirrorQueueCapacity) {

    static final long DEFAULT_MAX_BYTES = 25L * 1024 * 1024;
    static final List<String> DEFAULT_HOST_SUFFIXES =
            List.of("fbcdn.net", "cdninstagram.com", "fbsbx.com", "facebook.com");
    static final Duration DEFAULT_MAX_AGE = Duration.ofDays(3);
    static final Duration DEFAULT_RETRY_AFTER = Duration.ofMinutes(15);
    static final int DEFAULT_BATCH_SIZE = 20;
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);
    static final int DEFAULT_POOL_SIZE = 2;
    static final int DEFAULT_QUEUE_CAPACITY = 100;
    /** A byte array cannot hold more. */
    private static final long MAX_ARRAY_BYTES = Integer.MAX_VALUE - 8L;

    public AttachmentMirrorProperties {
        mirrorMaxBytes = mirrorMaxBytes == null || mirrorMaxBytes <= 0 ? DEFAULT_MAX_BYTES
                : Math.min(mirrorMaxBytes, MAX_ARRAY_BYTES);
        mirrorAllowedHostSuffixes = mirrorAllowedHostSuffixes == null || mirrorAllowedHostSuffixes.isEmpty()
                ? DEFAULT_HOST_SUFFIXES
                : mirrorAllowedHostSuffixes.stream().map(suffix -> suffix.strip().toLowerCase(Locale.ROOT))
                        .filter(suffix -> !suffix.isEmpty()).toList();
        mirrorMaxAge = positiveOr(mirrorMaxAge, DEFAULT_MAX_AGE);
        mirrorRetryAfter = positiveOr(mirrorRetryAfter, DEFAULT_RETRY_AFTER);
        mirrorBatchSize = mirrorBatchSize == null || mirrorBatchSize <= 0 ? DEFAULT_BATCH_SIZE : mirrorBatchSize;
        mirrorTimeout = positiveOr(mirrorTimeout, DEFAULT_TIMEOUT);
        mirrorPoolSize = mirrorPoolSize == null || mirrorPoolSize <= 0 ? DEFAULT_POOL_SIZE : mirrorPoolSize;
        mirrorQueueCapacity = mirrorQueueCapacity == null || mirrorQueueCapacity < 0 ? DEFAULT_QUEUE_CAPACITY
                : mirrorQueueCapacity;
    }

    private static Duration positiveOr(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }
}
