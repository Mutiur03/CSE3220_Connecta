package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.org.domain.Membership;
import java.util.UUID;

/** The conversation's current assignee. All assignee fields are null when it is unassigned. */
public record ConversationAssigneeResponse(UUID conversationId, UUID membershipId, UUID userId, String name) {

    public static ConversationAssigneeResponse from(Conversation conversation) {
        Membership assignee = conversation.getAssignee();
        if (assignee == null) {
            return new ConversationAssigneeResponse(conversation.getId(), null, null, null);
        }
        return new ConversationAssigneeResponse(conversation.getId(), assignee.getId(),
                assignee.getUser().getId(), assignee.getUser().getFullName());
    }
}
