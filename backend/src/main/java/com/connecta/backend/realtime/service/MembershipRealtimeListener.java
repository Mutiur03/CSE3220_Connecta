package com.connecta.backend.realtime.service;

import com.connecta.backend.org.service.MemberRemovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: when a member is removed, closes that user's live sessions for that organization once the
 * removal is committed. It never throws, so a close problem cannot affect the publisher.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipRealtimeListener {

    private final RealtimeSessionRegistry registry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMemberRemoved(MemberRemovedEvent event) {
        try {
            registry.closeSessions(event.organizationId(), event.userId(), RealtimeSessionRegistry.ACCESS_REVOKED);
        } catch (RuntimeException e) {
            log.warn("Closing realtime sessions failed for organization {}", event.organizationId(), e);
        }
    }
}
