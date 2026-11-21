package com.connecta.backend.org.dto;

/**
 * The response to creating an invitation. {@code token} is the raw token, shown once: it is never stored
 * and cannot be read again. The link is also emailed; this copy is the fallback if the email never arrives.
 */
public record InvitationCreatedResponse(InvitationResponse invitation, String token) {
}
