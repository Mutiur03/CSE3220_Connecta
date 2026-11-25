package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.meta.domain.ConnectedChannelAccount;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage;
import com.connecta.backend.org.domain.Organization;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The persist step of webhook intake: stores one normalized Meta message in one organization's inbox. The
 * customer and the conversation (organization, channel, thread) are found or created (a new conversation records
 * the connected account the webhook arrived on; later messages never change it), the message and its attachments
 * are saved in the same transaction unless the organization already has that provider message id, and a
 * {@link MessageReceivedEvent} is published. Only the history import reconciles a message it already has; a
 * duplicate webhook delivery leaves the stored row alone.
 * A live customer message marks the conversation unread; history imports leave the read state alone.
 * Every lookup is scoped by organization, so another tenant's rows are never read or reused.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncomingMessageService {

    private final ParticipantRepository participants;
    private final ConversationRepository conversations;
    private final ConversationParticipantRepository links;
    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final MessageReconciler reconciler;
    private final ApplicationEventPublisher publisher;

    /** Stores a webhook message. A message the organization already has is left exactly as it is. */
    @Transactional
    public void receive(ConnectedChannelAccount account, NormalizedMessage incoming) {
        if (messages.existsByOrganizationIdAndExternalMessageId(account.getOrganization().getId(),
                incoming.externalMessageId())) {
            log.debug("Skipping a message the organization already has");
            return;
        }
        insert(account, incoming, true);
    }

    /**
     * Reconciles one message read from Meta's history: a new one is stored with the same linking and tenant
     * scoping as {@link #receive}, but announces nothing (the import publishes one summary event); one the
     * organization already has is brought in line with Meta by {@link MessageReconciler}.
     */
    @Transactional
    public HistoryMessageOutcome importHistorical(ConnectedChannelAccount account, NormalizedMessage incoming) {
        Optional<Message> stored = messages.findByOrganizationIdAndExternalMessageId(
                account.getOrganization().getId(), incoming.externalMessageId());
        if (stored.isPresent()) {
            return reconciler.reconcile(stored.get(), incoming) ? HistoryMessageOutcome.UPDATED
                    : HistoryMessageOutcome.UNCHANGED;
        }
        insert(account, incoming, false);
        return HistoryMessageOutcome.INSERTED;
    }

    private void insert(ConnectedChannelAccount account, NormalizedMessage incoming, boolean announce) {
        Organization organization = account.getOrganization();
        Channel channel = channelOf(incoming.channel());
        Participant customer = participantFor(organization, channel, incoming.peerId());
        Optional<Conversation> existing = conversations.findByOrganizationIdAndChannelAndExternalThreadId(
                organization.getId(), channel, incoming.peerId());
        Conversation conversation = existing
                .orElseGet(() -> startConversation(organization, account, channel, customer, incoming.peerId()));
        Message saved = messages.save(toMessage(conversation, customer, incoming));
        saveAttachments(saved, incoming);
        conversation.recordMessageAt(incoming.sentAt());
        if (announce) {
            if (saved.getDirection() == MessageDirection.INBOUND) {
                conversation.markUnread();
            }
            publisher.publishEvent(new MessageReceivedEvent(organization.getId(), conversation.getId(),
                    saved.getId(), channel, saved.getDirection(), existing.isEmpty()));
        }
    }

    private static Channel channelOf(WebhookObject object) {
        return switch (object) {
            case PAGE -> Channel.MESSENGER;
            case INSTAGRAM -> Channel.INSTAGRAM;
        };
    }

    private Participant participantFor(Organization organization, Channel channel, String externalId) {
        return participants.findByOrganizationIdAndChannelAndExternalId(organization.getId(), channel, externalId)
                .orElseGet(() -> participants.save(new Participant(organization, channel, externalId, null)));
    }

    private Conversation startConversation(Organization organization, ConnectedChannelAccount account,
            Channel channel, Participant customer,
            String threadId) {
        Conversation created = new Conversation(organization, channel, threadId);
        account.linkTo(created);
        Conversation conversation = conversations.save(created);
        links.save(new ConversationParticipant(conversation, customer));
        return conversation;
    }

    private void saveAttachments(Message message, NormalizedMessage incoming) {
        if (!incoming.attachments().isEmpty()) {
            attachments.saveAll(incoming.attachments().stream().map(file -> MessageAttachment.fromProvider(message,
                    file.kind(), file.url(), file.fileName(), file.contentType())).toList());
        }
    }

    private static Message toMessage(Conversation conversation, Participant customer, NormalizedMessage incoming) {
        return switch (incoming.direction()) {
            case INBOUND -> Message.inbound(conversation, customer, incoming.externalMessageId(), incoming.text(),
                    incoming.sentAt());
            case OUTBOUND -> Message.outbound(conversation, null, incoming.externalMessageId(), incoming.text(),
                    incoming.sentAt());
        };
    }
}
