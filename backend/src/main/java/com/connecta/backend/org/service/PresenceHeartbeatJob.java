package com.connecta.backend.org.service;

import com.connecta.backend.config.PresenceProperties;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Every app.presence.heartbeat-interval stamps the open interval of each member who is connected, so a
 * restart can close it at the last stamp instead of dropping the whole session. Members inside their
 * grace period are gone and are not stamped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceHeartbeatJob {

    private final PresenceProperties properties;
    private final TaskScheduler scheduler;
    private final PresenceService presence;
    private final AvailabilityIntervalService intervals;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        scheduler.scheduleWithFixedDelay(this::beat, properties.heartbeatInterval());
    }

    void beat() {
        try {
            Set<UUID> online = presence.connectedMembershipIds();
            if (!online.isEmpty()) {
                intervals.heartbeat(online);
            }
        } catch (RuntimeException e) {
            log.warn("Presence heartbeat failed: {}", e.getClass().getSimpleName());
        }
    }
}
