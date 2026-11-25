package com.connecta.backend.inbox.service;

import java.util.UUID;

/**
 * Published (Observer) when our copy of an incoming attachment is stored, so open threads of the conversation
 * reload and serve the copy. Listeners react after commit.
 */
public record AttachmentMirroredEvent(UUID organizationId, UUID conversationId) {
}
