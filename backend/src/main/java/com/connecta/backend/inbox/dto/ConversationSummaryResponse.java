package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import java.time.Instant;
import java.util.UUID;

/** One row of the inbox list. The name and preview are null when the customer or message is unknown. */
public record ConversationSummaryResponse(UUID id, Channel channel, String participantName,
        String lastMessagePreview, Instant lastMessageAt) {

    public static final int PREVIEW_MAX_LENGTH = 140;

    public static ConversationSummaryResponse from(Conversation conversation, Participant participant,
            Message lastMessage) {
        return new ConversationSummaryResponse(conversation.getId(), conversation.getChannel(),
                participant == null ? null : participant.getDisplayName(),
                lastMessage == null ? null : preview(lastMessage.getBody()), conversation.getLastMessageAt());
    }

    private static String preview(String body) {
        String text = body.strip();
        return text.length() <= PREVIEW_MAX_LENGTH ? text : text.substring(0, PREVIEW_MAX_LENGTH);
    }
}
