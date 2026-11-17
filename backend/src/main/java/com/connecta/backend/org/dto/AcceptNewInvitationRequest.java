package com.connecta.backend.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Accepts an invitation and creates the account in one step. The email comes from the invitation. */
public record AcceptNewInvitationRequest(
        @NotBlank @Size(max = AcceptInvitationRequest.TOKEN_MAX_LENGTH) String token,
        @NotBlank @Size(max = 150) String fullName,
        // 72 is BCrypt's input limit; longer passwords would be silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password) {
}
