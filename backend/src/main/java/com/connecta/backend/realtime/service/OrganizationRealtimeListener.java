package com.connecta.backend.realtime.service;

import com.connecta.backend.org.service.OrganizationDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: when an organization is deleted, closes every live session of it once the deletion is committed.
 * It never throws, so a close problem cannot affect the publisher.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationRealtimeListener {

    private final RealtimeSessionRegistry registry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrganizationDeleted(OrganizationDeletedEvent event) {
        try {
            registry.closeOrganization(event.organizationId(), RealtimeSessionRegistry.ACCESS_REVOKED);
        } catch (RuntimeException e) {
            log.warn("Closing realtime sessions failed for deleted organization {}", event.organizationId(), e);
        }
    }
}
