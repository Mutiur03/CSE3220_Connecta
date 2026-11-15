package com.connecta.backend.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(@NotBlank @Size(max = AcceptInvitationRequest.TOKEN_MAX_LENGTH) String token) {

    /** The raw token is 43 URL-safe characters; the limit only keeps oversized input out of the lookup. */
    public static final int TOKEN_MAX_LENGTH = 128;
}
