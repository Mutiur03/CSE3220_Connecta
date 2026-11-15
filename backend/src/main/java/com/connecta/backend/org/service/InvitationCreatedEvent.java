package com.connecta.backend.org.service;

import java.util.UUID;

/**
 * Published when an invitation is created. It carries no token: a listener that sends the link will need
 * the raw token, which exists only in the create response, so that listener must take it another way.
 */
public record InvitationCreatedEvent(UUID invitationId, UUID organizationId, String email, String role) {
}
