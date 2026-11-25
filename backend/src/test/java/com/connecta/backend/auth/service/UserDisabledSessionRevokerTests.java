package com.connecta.backend.auth.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.connecta.backend.user.service.UserDisabledEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserDisabledSessionRevokerTests {

    private final SessionService sessions = mock(SessionService.class);

    @Test
    void aDisabledUsersRefreshTokensAreRevoked() {
        UUID userId = UUID.randomUUID();

        new UserDisabledSessionRevoker(sessions).onUserDisabled(new UserDisabledEvent(userId));

        verify(sessions).revokeAll(userId);
    }
}
