package com.connecta.backend.org.service;

import java.util.UUID;

/**
 * Published when an invitation is created. It carries the raw token so a listener can email the link; the
 * event lives in memory only, and {@link #toString()} leaves the token out so it cannot reach a log.
 */
public record InvitationCreatedEvent(UUID invitationId, UUID organizationId, String organizationName,
        String inviterName, String email, String role, String token) {

    @Override
    public String toString() {
        return "InvitationCreatedEvent[invitationId=" + invitationId + ", organizationId=" + organizationId + "]";
    }
}
