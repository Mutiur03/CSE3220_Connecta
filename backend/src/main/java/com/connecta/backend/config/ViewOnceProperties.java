package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How view-once (disappearing) Instagram photos are handled (app.meta.view-once.*). A blank or non-positive
 * duration falls back to the default.
 *
 * @param hide true: a received view-once photo is never stored, only noted; false (default): kept as a photo
 * @param markTtl how long the sender's echo is remembered to recognise the receiver's event (keep it small)
 * @param cleanupInterval time between deletions of expired marks
 */
@ConfigurationProperties(prefix = "app.meta.view-once")
public record ViewOnceProperties(boolean hide, Duration markTtl, Duration cleanupInterval) {

    static final Duration DEFAULT_MARK_TTL = Duration.ofHours(1);
    static final Duration DEFAULT_CLEANUP_INTERVAL = Duration.ofMinutes(10);

    public ViewOnceProperties {
        markTtl = positive(markTtl) ? markTtl : DEFAULT_MARK_TTL;
        cleanupInterval = positive(cleanupInterval) ? cleanupInterval : DEFAULT_CLEANUP_INTERVAL;
    }

    /** All defaults (photos are kept), for tests. */
    public static ViewOnceProperties defaults() {
        return new ViewOnceProperties(false, null, null);
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
