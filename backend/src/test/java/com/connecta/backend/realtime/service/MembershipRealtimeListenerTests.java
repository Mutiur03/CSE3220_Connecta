package com.connecta.backend.realtime.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.connecta.backend.org.service.MemberRemovedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MembershipRealtimeListenerTests {

    private final RealtimeSessionRegistry registry = mock(RealtimeSessionRegistry.class);
    private final MembershipRealtimeListener listener = new MembershipRealtimeListener(registry);
    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void aRemovedMembersSessionsInThatOrganizationAreClosed() {
        listener.onMemberRemoved(new MemberRemovedEvent(orgId, userId));

        verify(registry).closeSessions(orgId, userId, RealtimeSessionRegistry.ACCESS_REVOKED);
    }

    @Test
    void aCloseFailureDoesNotReachThePublisher() {
        doThrow(new IllegalStateException("boom")).when(registry).closeSessions(orgId, userId,
                RealtimeSessionRegistry.ACCESS_REVOKED);

        listener.onMemberRemoved(new MemberRemovedEvent(orgId, userId));
    }
}
