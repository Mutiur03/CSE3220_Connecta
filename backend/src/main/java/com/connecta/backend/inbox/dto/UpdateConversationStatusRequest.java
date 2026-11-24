package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.ConversationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateConversationStatusRequest(@NotNull ConversationStatus status) {
}
