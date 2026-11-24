package com.connecta.backend.realtime.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.connecta.backend.org.service.OrganizationDeletedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrganizationRealtimeListenerTests {

    private final RealtimeSessionRegistry registry = mock(RealtimeSessionRegistry.class);
    private final OrganizationRealtimeListener listener = new OrganizationRealtimeListener(registry);
    private final UUID orgId = UUID.randomUUID();

    @Test
    void aDeletedOrganizationsSessionsAreAllClosed() {
        listener.onOrganizationDeleted(new OrganizationDeletedEvent(orgId));

        verify(registry).closeOrganization(orgId, RealtimeSessionRegistry.ACCESS_REVOKED);
    }

    @Test
    void aCloseFailureDoesNotReachThePublisher() {
        doThrow(new IllegalStateException("boom")).when(registry).closeOrganization(orgId,
                RealtimeSessionRegistry.ACCESS_REVOKED);

        listener.onOrganizationDeleted(new OrganizationDeletedEvent(orgId));
    }
}
