package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the Messenger and Instagram history import (app.meta.history-import.*). The import reads every
 * conversation Meta has, and keeps the newest {@code messagesPerConversation} (50 by default) of each. A blank or
 * non-positive conversation or total-message cap means "no cap". {@code maxGraphPages} always bounds an import, as
 * a guard against a paging loop that never ends.
 *
 * @param enabled false turns the automatic import off (on connect and at startup); null means on
 * @param maxConversations optional cap on conversations read in one import; unset means all of them
 * @param messagesPerConversation most recent messages kept from one conversation (default 50)
 * @param maxMessages optional cap on messages handled (stored or skipped) in one import; unset means all
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

    /** The value of a count cap that is not set: effectively no limit. */
    public static final int UNLIMITED = Integer.MAX_VALUE;
    static final int DEFAULT_MESSAGES_PER_CONVERSATION = 50;
    static final int DEFAULT_PAGE_SIZE = 5;
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    static final int DEFAULT_WORKER_THREADS = 2;
    static final int DEFAULT_QUEUE_CAPACITY = 100;
    static final int DEFAULT_MAX_GRAPH_PAGES = 100_000;

    public HistoryImportProperties {
        enabled = enabled == null || enabled;
        maxConversations = maxConversations > 0 ? maxConversations : UNLIMITED;
        messagesPerConversation = messagesPerConversation > 0 ? messagesPerConversation
                : DEFAULT_MESSAGES_PER_CONVERSATION;
        maxMessages = maxMessages > 0 ? maxMessages : UNLIMITED;
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
