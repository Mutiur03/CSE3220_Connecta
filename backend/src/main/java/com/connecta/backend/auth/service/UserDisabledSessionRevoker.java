package com.connecta.backend.auth.service;

import com.connecta.backend.user.service.UserDisabledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer: when a user is disabled, revokes all of their refresh tokens in the same transaction as the
 * change, so the account never ends up disabled with live sign-ins. The user feature knows nothing of auth.
 */
@Component
@RequiredArgsConstructor
public class UserDisabledSessionRevoker {

    private final SessionService sessions;

    @EventListener
    public void onUserDisabled(UserDisabledEvent event) {
        sessions.revokeAll(event.userId());
    }
}
