package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short database step of sending a reply, run after Meta accepted the message. It is its own bean so the
 * {@code @Transactional} proxy applies when {@link OutgoingMessageService} or {@link OutgoingAttachmentService}
 * calls it, and each call is one short transaction. Entities are loaded here, never passed in, so nothing is used
 * lazily outside a transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutgoingMessageRecorder {

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /**
     * Stores the sent text message. When the webhook echo stored it first, that row is attributed to the agent
     * instead. A unique-constraint failure (the echo committed in between) surfaces as
     * {@link org.springframework.dao.DataIntegrityViolationException} and rolls this transaction back.
     */
    @Transactional
    public MessageResponse record(String subject, UUID organizationId, UUID conversationId, String externalId,
            String text) {
        return store(subject, organizationId, conversationId, externalId, text, List.of());
    }

    /**
     * Stores the sent attachment as a message labelled with its kind ({@code [Photo]} ...) plus its attachment
     * row. Same echo handling as {@link #record}.
     */
    @Transactional
    public MessageResponse recordAttachment(String subject, UUID organizationId, UUID conversationId,
            String externalId, OutgoingAttachment attachment) {
        return store(subject, organizationId, conversationId, externalId, attachment.kind().label(),
                List.of(attachment));
    }

    /** Attributes the already stored message in a fresh transaction. Empty when no such row exists. */
    @Transactional
    public Optional<MessageResponse> attributeStored(String subject, UUID organizationId, String externalId) {
        return attributeExisting(subject, organizationId, externalId, List.of());
    }

    /**
     * Attributes the already stored attachment message in a fresh transaction; adds the sent attachment only when
     * the echo carried none. Empty when no such row exists.
     */
    @Transactional
    public Optional<MessageResponse> attributeStored(String subject, UUID organizationId, String externalId,
            OutgoingAttachment attachment) {
        return attributeExisting(subject, organizationId, externalId, List.of(attachment));
    }

    private MessageResponse store(String subject, UUID organizationId, UUID conversationId, String externalId,
            String text, List<OutgoingAttachment> sent) {
        User agent = activeCaller.load(subject);
        Optional<Message> echoed = messages.findByOrganizationIdAndExternalMessageId(organizationId, externalId);
        if (echoed.isPresent()) {
            return attribute(echoed.get(), agent, sent);
        }
        Conversation conversation = conversations.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(ConversationNotFoundException::new);
        Instant now = clock.instant();
        Message saved = messages.saveAndFlush(Message.outbound(conversation, agent, externalId, text, now));
        List<MessageAttachment> files = saveAttachments(saved, sent);
        conversation.recordMessageAt(now);
        // A member who replies has read the thread.
        conversation.markRead();
        publish(saved);
        return MessageResponse.from(saved, files);
    }

    private Optional<MessageResponse> attributeExisting(String subject, UUID organizationId, String externalId,
            List<OutgoingAttachment> sent) {
        User agent = activeCaller.load(subject);
        return messages.findByOrganizationIdAndExternalMessageId(organizationId, externalId)
                .map(stored -> attribute(stored, agent, sent));
    }

    /** The echo row is kept (no duplicate); its own attachments win, the sent ones fill in only when it has none. */
    private MessageResponse attribute(Message stored, User agent, List<OutgoingAttachment> sent) {
        List<MessageAttachment> files = attachments
                .findByMessage(stored.getOrganization().getId(), List.of(stored.getId()))
                .getOrDefault(stored.getId(), List.of());
        if (files.isEmpty()) {
            files = saveAttachments(stored, sent);
        }
        if (stored.attributeTo(agent)) {
            log.info("The webhook echo stored a sent message first; attributed it to the sender");
            publish(stored);
        }
        return MessageResponse.from(stored, files);
    }

    private List<MessageAttachment> saveAttachments(Message message, List<OutgoingAttachment> sent) {
        if (sent.isEmpty()) {
            return List.of();
        }
        return attachments.saveAll(sent.stream().map(file -> new MessageAttachment(message, file.kind(), file.url(),
                file.fileName(), file.contentType())).toList());
    }

    private void publish(Message message) {
        publisher.publishEvent(new MessageReceivedEvent(message.getOrganization().getId(),
                message.getConversation().getId(), message.getId(), message.getConversation().getChannel(),
                message.getDirection(), false));
    }
}
