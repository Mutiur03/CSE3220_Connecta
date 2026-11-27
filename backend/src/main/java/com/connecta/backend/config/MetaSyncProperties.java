package com.connecta.backend.config;

import com.connecta.backend.meta.service.HistoryBounds;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The recent-message sync that backs up the webhook (app.meta.sync.*). Each run reads what changed since the last
 * successful one, so it adapts to how much was missed; the two counts are safety ceilings, not the amount read. A
 * blank or non-positive value falls back to the default, so a bad setting can never make a sync unbounded.
 *
 * @param enabled false turns the sync off; null means on
 * @param interval time between syncs, also the delay before the first scheduled one
 * @param maxConversations ceiling on conversations read per account in one run
 * @param messagesPerConversation ceiling on messages read per conversation in one run
 * @param overlap how far before the last successful sync a run starts reading, to absorb clock differences
 * @param firstWindow how far back the first run of an account that was never synced reads
 */
@ConfigurationProperties(prefix = "app.meta.sync")
public record MetaSyncProperties(Boolean enabled, Duration interval, int maxConversations,
        int messagesPerConversation, Duration overlap, Duration firstWindow) {

    static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(10);
    static final int DEFAULT_MAX_CONVERSATIONS = 1000;
    static final int DEFAULT_MESSAGES_PER_CONVERSATION = 200;
    static final Duration DEFAULT_OVERLAP = Duration.ofMinutes(15);
    static final Duration DEFAULT_FIRST_WINDOW = Duration.ofDays(1);

    public MetaSyncProperties {
        enabled = enabled == null || enabled;
        interval = interval != null && !interval.isZero() && !interval.isNegative() ? interval : DEFAULT_INTERVAL;
        maxConversations = maxConversations > 0 ? maxConversations : DEFAULT_MAX_CONVERSATIONS;
        messagesPerConversation = messagesPerConversation > 0 ? messagesPerConversation
                : DEFAULT_MESSAGES_PER_CONVERSATION;
        overlap = positive(overlap) ? overlap : DEFAULT_OVERLAP;
        firstWindow = positive(firstWindow) ? firstWindow : DEFAULT_FIRST_WINDOW;
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }

    /** All defaults, for tests. */
    public static MetaSyncProperties defaults() {
        return new MetaSyncProperties(null, null, 0, 0, null, null);
    }

    /**
     * Where a run starts reading: a little before the last successful sync, or a first window back for an account
     * that was never synced.
     */
    public Instant since(Instant lastSyncedAt, Instant now) {
        return lastSyncedAt != null ? lastSyncedAt.minus(overlap) : now.minus(firstWindow);
    }

    /** The limits of one run: everything since {@code since}, newest first, under the safety ceilings. */
    public HistoryBounds bounds(Instant since) {
        return new HistoryBounds(maxConversations, messagesPerConversation,
                maxConversations * messagesPerConversation, since);
    }
}
