package com.connecta.backend.realtime.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.connecta.backend.user.service.UserDisabledEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserRealtimeListenerTests {

    private final RealtimeSessionRegistry registry = mock(RealtimeSessionRegistry.class);
    private final UserRealtimeListener listener = new UserRealtimeListener(registry);
    private final UUID userId = UUID.randomUUID();

    @Test
    void aDisabledUsersSessionsAreClosedEverywhere() {
        listener.onUserDisabled(new UserDisabledEvent(userId));

        verify(registry).closeUser(userId, RealtimeSessionRegistry.ACCESS_REVOKED);
    }

    @Test
    void aCloseFailureDoesNotReachThePublisher() {
        doThrow(new IllegalStateException("boom")).when(registry).closeUser(userId,
                RealtimeSessionRegistry.ACCESS_REVOKED);

        listener.onUserDisabled(new UserDisabledEvent(userId));
    }
}
