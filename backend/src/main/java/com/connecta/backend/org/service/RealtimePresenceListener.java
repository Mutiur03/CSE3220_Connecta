package com.connecta.backend.org.service;

import com.connecta.backend.realtime.service.RealtimeConnectionClosed;
import com.connecta.backend.realtime.service.RealtimeConnectionOpened;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Observer: realtime connections opening and closing drive presence. It never throws, so a presence
 * problem cannot break a websocket handshake or its teardown.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimePresenceListener {

    private final PresenceService presence;

    @EventListener
    public void onOpened(RealtimeConnectionOpened event) {
        try {
            presence.connected(event.organizationId(), event.userId());
        } catch (RuntimeException e) {
            log.warn("Presence failed to record a connection for user {}", event.userId(), e);
        }
    }

    @EventListener
    public void onClosed(RealtimeConnectionClosed event) {
        try {
            presence.disconnected(event.organizationId(), event.userId());
        } catch (RuntimeException e) {
            log.warn("Presence failed to record a disconnect for user {}", event.userId(), e);
        }
    }
}
