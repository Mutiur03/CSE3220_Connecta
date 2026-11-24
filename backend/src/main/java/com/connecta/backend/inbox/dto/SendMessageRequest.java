package com.connecta.backend.inbox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reply an agent sends. 1000 is Instagram's text limit, the stricter of the two channels. */
public record SendMessageRequest(@NotBlank @Size(max = SendMessageRequest.TEXT_MAX_LENGTH) String text) {

    public static final int TEXT_MAX_LENGTH = 1000;
}
