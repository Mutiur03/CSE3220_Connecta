package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.ConversationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** One conversation with its customers, as the API returns it. Never exposes the entity itself. */
public record ConversationDetailResponse(
        UUID id,
        UUID organizationId,
        Channel channel,
        ConversationStatus status,
        List<ParticipantResponse> participants,
        Instant lastMessageAt,
        Instant createdAt,
        Instant updatedAt) {

    public static ConversationDetailResponse from(Conversation conversation, List<ConversationParticipant> links) {
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getOrganization().getId(),
                conversation.getChannel(),
                conversation.getStatus(),
                links.stream().map(link -> ParticipantResponse.from(link.getParticipant())).toList(),
                conversation.getLastMessageAt(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt());
    }
}
