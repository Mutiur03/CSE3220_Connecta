package com.connecta.backend.inbox.service;

import java.util.UUID;

/**
 * Published (Observer) after a conversation's assignee changed, by auto or manual assignment. Listeners react
 * after commit with {@code @TransactionalEventListener}.
 *
 * @param assigneeMembershipId the new assignee, or null when the conversation was unassigned
 */
public record AssignmentChangedEvent(UUID organizationId, UUID conversationId, UUID assigneeMembershipId) {
}
