package com.connecta.backend.org.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.connecta.backend.realtime.service.RealtimeConnectionClosed;
import com.connecta.backend.realtime.service.RealtimeConnectionOpened;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RealtimePresenceListenerTests {

    private final PresenceService presence = mock(PresenceService.class);
    private final RealtimePresenceListener listener = new RealtimePresenceListener(presence);
    private final UUID user = UUID.randomUUID();
    private final UUID org = UUID.randomUUID();

    @Test
    void anOpenedConnectionMarksThemConnected() {
        listener.onOpened(new RealtimeConnectionOpened(user, org));

        verify(presence).connected(org, user);
    }

    @Test
    void aClosedConnectionMarksThemDisconnected() {
        listener.onClosed(new RealtimeConnectionClosed(user, org));

        verify(presence).disconnected(org, user);
    }

    @Test
    void aPresenceFailureNeverReachesTheWebsocket() {
        doThrow(new IllegalStateException("boom")).when(presence).connected(org, user);
        doThrow(new IllegalStateException("boom")).when(presence).disconnected(org, user);

        listener.onOpened(new RealtimeConnectionOpened(user, org));
        listener.onClosed(new RealtimeConnectionClosed(user, org));
    }
}
