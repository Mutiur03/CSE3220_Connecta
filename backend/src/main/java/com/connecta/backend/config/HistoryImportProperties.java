package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds of the Messenger and Instagram history import (app.meta.history-import.*). A blank or non-positive
 * value falls back to the default, so a bad setting can never make an import unbounded.
 *
 * @param enabled false turns the automatic import off (on connect and at startup); null means on
 * @param maxConversations most conversations read in one import
 * @param messagesPerConversation most recent messages read from one conversation
 * @param maxMessages most messages handled (stored or skipped) in one import
 * @param pageSize conversations asked of Meta per request
 * @param requestTimeout connect and read timeout of each Meta request
 * @param workerThreads imports that may run at once on this server
 * @param queueCapacity imports that may wait; one more is dropped and retried at the next start
 * @param maxGraphPages most Graph requests (conversation and message pages) in one import; a loop guard. Single
 *        message lookups (shared posts) have a budget of the same size, counted apart so they cannot starve paging
 */
@ConfigurationProperties(prefix = "app.meta.history-import")
public record HistoryImportProperties(Boolean enabled, int maxConversations, int messagesPerConversation,
        int maxMessages, int pageSize, Duration requestTimeout, int workerThreads, int queueCapacity,
        int maxGraphPages) {

    static final int DEFAULT_MAX_CONVERSATIONS = 50;
    static final int DEFAULT_MESSAGES_PER_CONVERSATION = 50;
    static final int DEFAULT_MAX_MESSAGES = 1000;
    static final int DEFAULT_PAGE_SIZE = 5;
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    static final int DEFAULT_WORKER_THREADS = 2;
    static final int DEFAULT_QUEUE_CAPACITY = 100;
    static final int DEFAULT_MAX_GRAPH_PAGES = 200;

    public HistoryImportProperties {
        enabled = enabled == null || enabled;
        maxConversations = maxConversations > 0 ? maxConversations : DEFAULT_MAX_CONVERSATIONS;
        messagesPerConversation = messagesPerConversation > 0 ? messagesPerConversation
                : DEFAULT_MESSAGES_PER_CONVERSATION;
        maxMessages = maxMessages > 0 ? maxMessages : DEFAULT_MAX_MESSAGES;
        pageSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        requestTimeout = requestTimeout != null && !requestTimeout.isZero() && !requestTimeout.isNegative()
                ? requestTimeout : DEFAULT_TIMEOUT;
        workerThreads = workerThreads > 0 ? workerThreads : DEFAULT_WORKER_THREADS;
        queueCapacity = queueCapacity > 0 ? queueCapacity : DEFAULT_QUEUE_CAPACITY;
        maxGraphPages = maxGraphPages > 0 ? maxGraphPages : DEFAULT_MAX_GRAPH_PAGES;
    }

    /** All defaults, for tests. */
    public static HistoryImportProperties defaults() {
        return new HistoryImportProperties(null, 0, 0, 0, 0, null, 0, 0, 0);
    }
}
