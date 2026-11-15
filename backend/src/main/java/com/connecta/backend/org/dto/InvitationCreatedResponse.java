package com.connecta.backend.org.dto;

/**
 * The response to creating an invitation. {@code token} is the raw token, shown once: it is never stored
 * and cannot be read again. There is no email service yet, so the admin passes the token on by hand.
 */
public record InvitationCreatedResponse(InvitationResponse invitation, String token) {
}
