package com.connecta.backend.inbox.service;

import java.util.UUID;

/** Published (Observer) when a customer's display name was stored, so open inboxes can refresh. */
public record ParticipantProfileUpdatedEvent(UUID organizationId, UUID conversationId) {
}
