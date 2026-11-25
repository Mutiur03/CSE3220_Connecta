package com.connecta.backend.meta.service;

/**
 * What one import did.
 *
 * @param conversations conversations read
 * @param messagesInserted messages the organization did not have, now stored
 * @param messagesUpdated stored messages brought in line with Meta (body corrected or attachments added)
 * @param messagesUnchanged stored messages that already matched Meta
 * @param truncated true when a limit stopped the import before Meta ran out of history
 */
public record HistoryImportResult(int conversations, int messagesInserted, int messagesUpdated,
        int messagesUnchanged, boolean truncated) {
}
