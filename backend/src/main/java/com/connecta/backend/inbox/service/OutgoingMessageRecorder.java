package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short database step of sending a reply, run after Meta accepted the message. It is its own bean so the
 * {@code @Transactional} proxy applies when {@link OutgoingMessageService} calls it, and each call is one
 * short transaction. Entities are loaded here, never passed in, so nothing is used lazily outside a transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutgoingMessageRecorder {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /**
     * Stores the sent message. When the webhook echo stored it first, that row is attributed to the agent
     * instead. A unique-constraint failure (the echo committed in between) surfaces as
     * {@link org.springframework.dao.DataIntegrityViolationException} and rolls this transaction back.
     */
    @Transactional
    public MessageResponse record(String subject, UUID organizationId, UUID conversationId, String externalId,
            String text) {
        User agent = activeCaller.load(subject);
        Optional<Message> echoed = messages.findByOrganizationIdAndExternalMessageId(organizationId, externalId);
        if (echoed.isPresent()) {
            return attribute(echoed.get(), agent);
        }
        Conversation conversation = conversations.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(ConversationNotFoundException::new);
        Instant now = clock.instant();
        Message saved = messages.saveAndFlush(Message.outbound(conversation, agent, externalId, text, now));
        conversation.recordMessageAt(now);
        publish(saved);
        return MessageResponse.from(saved);
    }

    /** Attributes the already stored message in a fresh transaction. Empty when no such row exists. */
    @Transactional
    public Optional<MessageResponse> attributeStored(String subject, UUID organizationId, String externalId) {
        User agent = activeCaller.load(subject);
        return messages.findByOrganizationIdAndExternalMessageId(organizationId, externalId)
                .map(stored -> attribute(stored, agent));
    }

    private MessageResponse attribute(Message stored, User agent) {
        if (stored.attributeTo(agent)) {
            log.info("The webhook echo stored a sent message first; attributed it to the sender");
            publish(stored);
        }
        return MessageResponse.from(stored);
    }

    private void publish(Message message) {
        publisher.publishEvent(new MessageReceivedEvent(message.getOrganization().getId(),
                message.getConversation().getId(), message.getId(), message.getConversation().getChannel(),
                message.getDirection(), false));
    }
}
