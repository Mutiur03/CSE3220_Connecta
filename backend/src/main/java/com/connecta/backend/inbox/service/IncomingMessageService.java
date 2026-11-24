package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
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
 * customer and the conversation (organization, channel, thread) are found or created (a new conversation records the connected account the webhook arrived on; later messages never change it), the message is saved
 * unless the organization already has that provider message id, and a {@link MessageReceivedEvent} is published.
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
    private final ApplicationEventPublisher publisher;

    @Transactional
    public void receive(ConnectedChannelAccount account, NormalizedMessage incoming) {
        store(account, incoming, true);
    }

    /**
     * Stores a message read from Meta's history: same dedupe, linking and tenant scoping as {@link #receive}, but
     * announces nothing (the import publishes one summary event). Returns false when it was already stored.
     */
    @Transactional
    public boolean importHistorical(ConnectedChannelAccount account, NormalizedMessage incoming) {
        return store(account, incoming, false);
    }

    private boolean store(ConnectedChannelAccount account, NormalizedMessage incoming, boolean announce) {
        Organization organization = account.getOrganization();
        if (messages.existsByOrganizationIdAndExternalMessageId(organization.getId(), incoming.externalMessageId())) {
            log.debug("Skipping a message the organization already has");
            return false;
        }
        Channel channel = channelOf(incoming.channel());
        Participant customer = participantFor(organization, channel, incoming.peerId());
        Optional<Conversation> existing = conversations.findByOrganizationIdAndChannelAndExternalThreadId(
                organization.getId(), channel, incoming.peerId());
        Conversation conversation = existing
                .orElseGet(() -> startConversation(organization, account, channel, customer, incoming.peerId()));
        Message saved = messages.save(toMessage(conversation, customer, incoming));
        conversation.recordMessageAt(incoming.sentAt());
        if (announce) {
            publisher.publishEvent(new MessageReceivedEvent(organization.getId(), conversation.getId(),
                    saved.getId(), channel, saved.getDirection(), existing.isEmpty()));
        }
        return true;
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

    private static Message toMessage(Conversation conversation, Participant customer, NormalizedMessage incoming) {
        return switch (incoming.direction()) {
            case INBOUND -> Message.inbound(conversation, customer, incoming.externalMessageId(), incoming.text(),
                    incoming.sentAt());
            case OUTBOUND -> Message.outbound(conversation, null, incoming.externalMessageId(), incoming.text(),
                    incoming.sentAt());
        };
    }
}
