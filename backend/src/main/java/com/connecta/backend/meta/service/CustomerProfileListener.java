package com.connecta.backend.meta.service;

import com.connecta.backend.config.ProfileLookupConfig;
import com.connecta.backend.config.ProfileLookupProperties;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.inbox.service.ParticipantProfileService;
import com.connecta.backend.inbox.service.ProfileTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: after a message is committed, looks the customer up on Meta when the profile is due (no name, never
 * looked up, or the stored picture is older than the TTL) and saves name and picture. After a history import it
 * does the same for a bounded number of customers never looked up. It runs on a small bounded executor so the
 * webhook answer never waits for Meta, holds no database connection during the HTTP call, and never throws: a
 * failed lookup keeps the stored values and is retried by the next inbound message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileListener {

    private final ParticipantProfileService profiles;
    private final CustomerProfileClientFactory clients;
    private final ProfileLookupProperties lookup;

    @Async(ProfileLookupConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            profiles.findNeedingRefresh(event.organizationId(), event.conversationId()).ifPresent(this::refresh);
        } catch (RuntimeException e) {
            log.warn("Customer profile lookup failed for conversation {}: {}", event.conversationId(),
                    e.getClass().getSimpleName());
        }
    }

    /** One task for the whole import, capped by {@code import-refresh-limit}, so Meta is never stormed. */
    @Async(ProfileLookupConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHistoryImported(HistoryImportedEvent event) {
        try {
            profiles.findNeverLookedUp(event.organizationId(), lookup.importRefreshLimit())
                    .forEach(this::refreshQuietly);
        } catch (RuntimeException e) {
            log.warn("Customer profile refresh after import failed: {}", e.getClass().getSimpleName());
        }
    }

    private void refreshQuietly(ProfileTarget customer) {
        try {
            refresh(customer);
        } catch (RuntimeException e) {
            log.warn("Customer profile lookup failed for conversation {}: {}", customer.conversationId(),
                    e.getClass().getSimpleName());
        }
    }

    private void refresh(ProfileTarget customer) {
        clients.forChannel(customer.channel()).fetchProfile(customer)
                .ifPresent(profile -> profiles.storeProfile(customer, profile.name(), profile.pictureUrl()));
    }
}
