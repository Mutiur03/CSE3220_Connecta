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
    public static final String PARTICIPANT_UPDATED = "participant.updated";
    public static final String INBOX_REFRESH = "inbox.refresh";

    /** A customer name was stored: only the conversation id is set, and clients refresh that conversation. */
    public static RealtimeEnvelope participantUpdated(UUID conversationId) {
        return new RealtimeEnvelope(PARTICIPANT_UPDATED, conversationId, null, null, null, false, null);
    }

    /** Tells open inboxes to reload: many conversations changed at once (a history import). */
    public static RealtimeEnvelope inboxRefresh() {
        return new RealtimeEnvelope(INBOX_REFRESH, null, null, null, null, false, null);
    }

    public static RealtimeEnvelope messageCreated(MessageReceivedEvent event, MessageResponse message) {
        return new RealtimeEnvelope(MESSAGE_CREATED, event.conversationId(), event.messageId(), event.channel(),
                event.direction(), event.newConversation(), message);
    }
}
