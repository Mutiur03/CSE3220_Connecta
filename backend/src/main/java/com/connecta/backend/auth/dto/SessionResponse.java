package com.connecta.backend.auth.dto;

import com.connecta.backend.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.UUID;

/** One signed-in device. {@code current} marks the session the request itself belongs to. */
public record SessionResponse(
        UUID id, String userAgent, String ip, String location, Instant createdAt, Instant lastUsedAt, boolean current) {

    public static SessionResponse from(RefreshToken token, UUID currentSessionId) {
        return new SessionResponse(token.getSessionId(), token.getUserAgent(), token.getIp(), token.getLocation(),
                token.getCreatedAt(), token.getLastUsedAt(), token.getSessionId().equals(currentSessionId));
    }
}
