package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.domain.MessageDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One message as the API returns it. The sender is the customer for an inbound message and the agent for
 * an outbound one; both are null once that row has been removed. {@code attachments} is never null; a message
 * sent with only attachments keeps a label such as {@code [Photo]} as its text.
 */
public record MessageResponse(
        UUID id,
        MessageDirection direction,
        String text,
        Instant sentAt,
        UUID senderId,
        String senderName,
        List<AttachmentResponse> attachments) {

    public MessageResponse {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static MessageResponse from(Message message, List<MessageAttachment> attachments) {
        List<AttachmentResponse> files = attachments.stream().map(AttachmentResponse::from).toList();
        if (message.getDirection() == MessageDirection.OUTBOUND) {
            var agent = message.getSentByUser();
            return new MessageResponse(message.getId(), message.getDirection(), message.getBody(),
                    message.getSentAt(), agent == null ? null : agent.getId(),
                    agent == null ? null : agent.getFullName(), files);
        }
        var customer = message.getSenderParticipant();
        return new MessageResponse(message.getId(), message.getDirection(), message.getBody(),
                message.getSentAt(), customer == null ? null : customer.getId(),
                customer == null ? null : customer.getDisplayName(), files);
    }
}
