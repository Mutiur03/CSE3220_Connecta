package com.connecta.backend.meta.service;

/**
 * What one import did.
 *
 * @param conversations conversations read
 * @param messagesImported messages stored
 * @param messagesSkipped messages the organization already had
 * @param truncated true when a limit stopped the import before Meta ran out of history
 */
public record HistoryImportResult(int conversations, int messagesImported, int messagesSkipped, boolean truncated) {
}
