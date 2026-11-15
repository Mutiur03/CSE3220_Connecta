package com.connecta.backend.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Invitation settings (app.invitations.*). The ttl is how long an invitation can be accepted. */
@Validated
@ConfigurationProperties(prefix = "app.invitations")
public record InvitationProperties(@NotNull Duration ttl) {
}
