package com.connecta.backend.meta.service;

import com.connecta.backend.config.ProfileLookupConfig;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import com.connecta.backend.inbox.service.ParticipantProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: after a message is committed, fetches the customer's name from Meta when none is stored and saves it.
 * It runs on a small bounded executor so the webhook answer never waits for Meta, holds no database connection
 * during the HTTP call, and never throws: a failed lookup is retried by the next inbound message.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerProfileListener {

    private final ParticipantProfileService profiles;
    private final CustomerProfileClientFactory clients;

    @Async(ProfileLookupConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            profiles.findNameless(event.organizationId(), event.conversationId()).ifPresent(customer ->
                    clients.forChannel(customer.channel()).fetchName(customer)
                            .ifPresent(name -> profiles.storeName(customer, name)));
        } catch (RuntimeException e) {
            log.warn("Customer profile lookup failed for conversation {}: {}", event.conversationId(),
                    e.getClass().getSimpleName());
        }
    }
}
