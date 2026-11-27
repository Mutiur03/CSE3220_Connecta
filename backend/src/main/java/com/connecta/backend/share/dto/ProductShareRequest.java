package com.connecta.backend.share.dto;

import com.connecta.backend.inbox.dto.SendMessageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** The product to share and the text to send with it, which the agent may have edited from the preview. */
public record ProductShareRequest(
        @NotNull UUID productId,
        @NotBlank @Size(max = SendMessageRequest.TEXT_MAX_LENGTH) String text) {
}
