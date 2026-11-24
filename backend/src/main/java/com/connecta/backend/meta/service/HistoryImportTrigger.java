package com.connecta.backend.meta.service;

import com.connecta.backend.config.HistoryImportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: starts history imports. A connect starts the account import once the connection is committed; at
 * startup every connection never imported to the end (connected before this existed, or a failed run) is
 * imported in the background. Neither blocks the caller, and neither can fail it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class HistoryImportTrigger {

    private final HistoryImportScheduler scheduler;
    private final HistoryAccountStore store;
    private final HistoryImportProperties properties;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onConnected(ChannelConnectedEvent event) {
        if (properties.enabled()) {
            scheduler.submit(event.channel(), event.accountId());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void catchUp() {
        if (!properties.enabled()) {
            return;
        }
        try {
            scheduler.submitAll(store.pending());
        } catch (RuntimeException e) {
            log.warn("History import catch-up could not start: {}", e.getClass().getSimpleName());
        }
    }
}
