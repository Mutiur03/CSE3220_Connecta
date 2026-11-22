package com.connecta.backend.meta.dto;

import com.connecta.backend.meta.domain.MetaAuthorization;
import java.time.Instant;

/** Whether the organization is connected to Meta. The token itself is never returned. */
public record MetaAuthorizationStatusResponse(boolean connected, Instant connectedAt, Instant expiresAt) {

    public static MetaAuthorizationStatusResponse from(MetaAuthorization grant) {
        return new MetaAuthorizationStatusResponse(true, grant.getUpdatedAt(), grant.getExpiresAt());
    }

    public static MetaAuthorizationStatusResponse notConnected() {
        return new MetaAuthorizationStatusResponse(false, null, null);
    }
}
