package com.connecta.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Live inbox updates (app.realtime.*). A ticket lives only {@code ticketTtl}; a session that sends nothing
 * (the client pings) for {@code idleTimeout} is closed by a sweep every {@code sweepInterval}.
 */
@ConfigurationProperties(prefix = "app.realtime")
public record RealtimeProperties(
        @DefaultValue("30s") Duration ticketTtl,
        @DefaultValue("90s") Duration idleTimeout,
        @DefaultValue("30s") Duration sweepInterval,
        @DefaultValue("5") int maxSessionsPerUser,
        @DefaultValue("5s") Duration sendTimeLimit,
        @DefaultValue("65536") int sendBufferSizeLimit) {
}
