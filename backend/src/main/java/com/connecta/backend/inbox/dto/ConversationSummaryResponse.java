package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.org.domain.Membership;
import java.time.Instant;
import java.util.UUID;

/** One row of the inbox list. The name, avatar URL (an https Meta picture link that may expire) and preview are null when unknown. */
public record ConversationSummaryResponse(UUID id, Channel channel, ConversationStatus status, boolean unread,
        String participantName,
        String avatarUrl, String lastMessagePreview, Instant lastMessageAt,
        Assignee assignee) {

    /** The member handling the conversation; {@code online} is connected now or inside the presence grace period. */
    public record Assignee(UUID membershipId, UUID userId, String name, boolean online) {
    }

    public static final int PREVIEW_MAX_LENGTH = 140;

    public static ConversationSummaryResponse from(Conversation conversation, Participant participant,
            Message lastMessage, boolean assigneeOnline) {
        return new ConversationSummaryResponse(conversation.getId(), conversation.getChannel(),
                conversation.getStatus(),
                conversation.isUnread(),
                participant == null ? null : participant.getDisplayName(),
                participant == null ? null : participant.getProfilePictureUrl(),
                lastMessage == null ? null : preview(lastMessage.getBody()), conversation.getLastMessageAt(),
                assignee(conversation.getAssignee(), assigneeOnline));
    }

    private static Assignee assignee(Membership member, boolean online) {
        return member == null ? null
                : new Assignee(member.getId(), member.getUser().getId(), member.getUser().getFullName(), online);
    }

    private static String preview(String body) {
        String text = body.strip();
        return text.length() <= PREVIEW_MAX_LENGTH ? text : text.substring(0, PREVIEW_MAX_LENGTH);
    }
}
