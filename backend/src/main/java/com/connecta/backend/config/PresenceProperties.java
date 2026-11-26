package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Online presence (app.presence.*). A member's last realtime connection must stay gone for
 * {@code gracePeriod} before they count as offline, so a page refresh does not split their online time.
 * While they are connected, their open interval is stamped every {@code heartbeatInterval}, so a backend
 * restart loses at most that much of a session.
 */
@ConfigurationProperties(prefix = "app.presence")
public record PresenceProperties(@DefaultValue("60s") Duration gracePeriod,
        @DefaultValue("30s") Duration heartbeatInterval) {
}
