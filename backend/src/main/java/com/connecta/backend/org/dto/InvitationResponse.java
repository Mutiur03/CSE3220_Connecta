package com.connecta.backend.org.dto;

import com.connecta.backend.org.domain.Invitation;
import java.time.Instant;
import java.util.UUID;

/** An invitation as listed to an admin. It never carries the token or its hash. */
public record InvitationResponse(UUID id, String email, String role, String status, Instant expiresAt,
        Instant createdAt) {

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(invitation.getId(), invitation.getEmail(), invitation.getRole().getName(),
                invitation.getStatus().name(), invitation.getExpiresAt(), invitation.getCreatedAt());
    }
}
