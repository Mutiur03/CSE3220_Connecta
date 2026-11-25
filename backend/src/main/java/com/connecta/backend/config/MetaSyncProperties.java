package com.connecta.backend.config;

import com.connecta.backend.meta.service.HistoryBounds;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The recent-message sync that backs up the webhook (app.meta.sync.*). A blank or non-positive value falls back
 * to the default, so a bad setting can never make a sync unbounded.
 *
 * @param enabled false turns the sync off; null means on
 * @param interval time between syncs, also the delay before the first scheduled one
 * @param maxConversations newest conversations read per account
 * @param messagesPerConversation newest messages read per conversation
 */
@ConfigurationProperties(prefix = "app.meta.sync")
public record MetaSyncProperties(Boolean enabled, Duration interval, int maxConversations,
        int messagesPerConversation) {

    static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(10);
    static final int DEFAULT_MAX_CONVERSATIONS = 10;
    static final int DEFAULT_MESSAGES_PER_CONVERSATION = 10;

    public MetaSyncProperties {
        enabled = enabled == null || enabled;
        interval = interval != null && !interval.isZero() && !interval.isNegative() ? interval : DEFAULT_INTERVAL;
        maxConversations = maxConversations > 0 ? maxConversations : DEFAULT_MAX_CONVERSATIONS;
        messagesPerConversation = messagesPerConversation > 0 ? messagesPerConversation
                : DEFAULT_MESSAGES_PER_CONVERSATION;
    }

    /** All defaults, for tests. */
    public static MetaSyncProperties defaults() {
        return new MetaSyncProperties(null, null, 0, 0);
    }

    /** The read limits of one sync: the newest conversations, a few messages each. */
    public HistoryBounds bounds() {
        return new HistoryBounds(maxConversations, messagesPerConversation,
                maxConversations * messagesPerConversation);
    }
}
