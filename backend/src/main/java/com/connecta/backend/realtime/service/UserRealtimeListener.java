package com.connecta.backend.realtime.service;

import com.connecta.backend.user.service.UserDisabledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer: when a user is disabled, closes all of that user's live sessions, in every organization, once
 * the change is committed. It never throws, so a close problem cannot affect the publisher.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRealtimeListener {

    private final RealtimeSessionRegistry registry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserDisabled(UserDisabledEvent event) {
        try {
            registry.closeUser(event.userId(), RealtimeSessionRegistry.ACCESS_REVOKED);
        } catch (RuntimeException e) {
            log.warn("Closing realtime sessions failed for user {}", event.userId(), e);
        }
    }
}
