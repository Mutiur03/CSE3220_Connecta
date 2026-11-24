package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short database steps around a customer profile lookup: find the customer of a conversation that
 * still has no name, and store a name once Meta returned it. The HTTP call happens between them, outside any
 * transaction. Every lookup is scoped by organization.
 */
@Service
@RequiredArgsConstructor
public class ParticipantProfileService {

    private final ConversationRepository conversations;
    private final ParticipantRepository participants;
    private final ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public Optional<NamelessCustomer> findNameless(UUID organizationId, UUID conversationId) {
        return conversations.findByIdAndOrganizationId(conversationId, organizationId)
                .filter(conversation -> customerOf(conversation).map(Participant::isNameless).orElse(false))
                .map(ParticipantProfileService::toNameless);
    }

    /** Stores the name unless one is already there (never overwrites) and announces it. Returns whether it stored. */
    @Transactional
    public boolean storeName(NamelessCustomer customer, String name) {
        boolean stored = participants.findByOrganizationIdAndChannelAndExternalId(customer.organizationId(),
                customer.channel(), customer.externalId()).map(p -> p.nameIfAbsent(name)).orElse(false);
        if (stored) {
            publisher.publishEvent(new ParticipantProfileUpdatedEvent(customer.organizationId(),
                    customer.conversationId()));
        }
        return stored;
    }

    private Optional<Participant> customerOf(Conversation conversation) {
        return participants.findByOrganizationIdAndChannelAndExternalId(conversation.getOrganization().getId(),
                conversation.getChannel(), conversation.getExternalThreadId());
    }

    private static NamelessCustomer toNameless(Conversation c) {
        return new NamelessCustomer(c.getOrganization().getId(), c.getId(), c.getChannel(), c.getExternalThreadId(),
                c.getMetaPage() == null ? null : c.getMetaPage().getId(),
                c.getInstagramAccount() == null ? null : c.getInstagramAccount().getId());
    }
}
