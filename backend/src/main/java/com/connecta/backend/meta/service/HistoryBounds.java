package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;

/**
 * How much of an account's history one read may take: the full import and the recent-message sync differ only
 * in these numbers.
 *
 * @param maxConversations most conversations read, newest first
 * @param messagesPerConversation most recent messages kept from one conversation
 * @param maxMessages most messages handled in one read
 */
public record HistoryBounds(int maxConversations, int messagesPerConversation, int maxMessages) {

    /** The bounds of a full history import. */
    public static HistoryBounds of(HistoryImportProperties limits) {
        return new HistoryBounds(limits.maxConversations(), limits.messagesPerConversation(), limits.maxMessages());
    }
}
