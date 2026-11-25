package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import java.util.UUID;

/**
 * Published (Observer) once a Meta message is saved in the inbox. Listeners should react after commit with
 * {@code @TransactionalEventListener}, so they only ever see rows that exist.
 *
 * @param newConversation true when this message started the conversation (assignment reacts to that)
 */
public record MessageReceivedEvent(UUID organizationId, UUID conversationId, UUID messageId, Channel channel,
        MessageDirection direction, boolean newConversation) {
}
