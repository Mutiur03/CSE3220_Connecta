package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import java.time.Instant;

/**
 * How much of an account's history one read may take: the full import and the recent-message sync differ only
 * in these numbers.
 *
 * @param maxConversations most conversations read, newest first
 * @param messagesPerConversation most recent messages kept from one conversation
 * @param maxMessages most messages handled in one read
 * @param since when set, only what happened at or after this moment is read: the read stops at the first
 *        conversation whose newest message is older, and inside a conversation at the first older message. The
 *        counts above then act as safety ceilings. Null means the counts alone bound the read.
 */
public record HistoryBounds(int maxConversations, int messagesPerConversation, int maxMessages, Instant since) {

    /** A read bounded by counts alone. */
    public HistoryBounds(int maxConversations, int messagesPerConversation, int maxMessages) {
        this(maxConversations, messagesPerConversation, maxMessages, null);
    }

    /** The bounds of a full history import. */
    public static HistoryBounds of(HistoryImportProperties limits) {
        return new HistoryBounds(limits.maxConversations(), limits.messagesPerConversation(), limits.maxMessages());
    }
}
