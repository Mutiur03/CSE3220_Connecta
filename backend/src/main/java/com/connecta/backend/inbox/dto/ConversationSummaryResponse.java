package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import java.time.Instant;
import java.util.UUID;

/** One row of the inbox list. The name, avatar URL (an https Meta picture link that may expire) and preview are null when unknown. */
public record ConversationSummaryResponse(UUID id, Channel channel, ConversationStatus status,
        String participantName,
        String avatarUrl, String lastMessagePreview, Instant lastMessageAt) {

    public static final int PREVIEW_MAX_LENGTH = 140;

    public static ConversationSummaryResponse from(Conversation conversation, Participant participant,
            Message lastMessage) {
        return new ConversationSummaryResponse(conversation.getId(), conversation.getChannel(),
                conversation.getStatus(),
                participant == null ? null : participant.getDisplayName(),
                participant == null ? null : participant.getProfilePictureUrl(),
                lastMessage == null ? null : preview(lastMessage.getBody()), conversation.getLastMessageAt());
    }

    private static String preview(String body) {
        String text = body.strip();
        return text.length() <= PREVIEW_MAX_LENGTH ? text : text.substring(0, PREVIEW_MAX_LENGTH);
    }
}
