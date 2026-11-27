package com.connecta.backend.inbox.dto;

import java.util.UUID;

/** Who takes the conversation: a membership id of the same organization, or null to unassign. */
public record AssignConversationRequest(UUID membershipId) {
}
