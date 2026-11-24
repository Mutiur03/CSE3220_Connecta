package com.connecta.backend.realtime.dto;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.service.MessageReceivedEvent;
import java.util.UUID;

/** What the server pushes over /ws/inbox. {@code type} is {@code message.created} (a new inbound or outbound message). */
public record RealtimeEnvelope(
        String type,
        UUID conversationId,
        UUID messageId,
        Channel channel,
        MessageDirection direction,
        boolean newConversation,
        MessageResponse message) {

    public static final String MESSAGE_CREATED = "message.created";

    public static RealtimeEnvelope messageCreated(MessageReceivedEvent event, MessageResponse message) {
        return new RealtimeEnvelope(MESSAGE_CREATED, event.conversationId(), event.messageId(), event.channel(),
                event.direction(), event.newConversation(), message);
    }
}
