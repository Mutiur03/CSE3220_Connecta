package com.connecta.backend.inbox.service;

import com.connecta.backend.config.ProfileLookupProperties;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two short database steps around a customer profile lookup: find the customer of a conversation whose
 * profile is due (no name, never looked up, or older than the picture TTL) and store the result. The HTTP call
 * happens between them, outside any transaction. Every lookup is scoped by organization.
 */
@Service
@RequiredArgsConstructor
public class ParticipantProfileService {

    private final ConversationRepository conversations;
    private final ParticipantRepository participants;
    private final ApplicationEventPublisher publisher;
    private final ProfileLookupProperties lookup;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<ProfileTarget> findNeedingRefresh(UUID organizationId, UUID conversationId) {
        return conversations.findByIdAndOrganizationId(conversationId, organizationId)
                .filter(conversation -> customerOf(conversation)
                        .map(p -> p.needsProfileRefresh(clock.instant(), lookup.pictureTtl())).orElse(false))
                .map(ParticipantProfileService::toTarget);
    }

    /** Customers of an organization never looked up (newest conversations first), at most {@code limit}. */
    @Transactional(readOnly = true)
    public List<ProfileTarget> findNeverLookedUp(UUID organizationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return conversations.findWithUnfetchedCustomer(organizationId, PageRequest.of(0, limit)).stream()
                .map(ParticipantProfileService::toTarget).toList();
    }

    /**
     * Stores a successful lookup: the name only when none is stored (never overwrites), the picture replaced by
     * the newer one, and the lookup time. Announces a change. Returns whether the name or picture changed.
     */
    @Transactional
    public boolean storeProfile(ProfileTarget customer, String name, String pictureUrl) {
        boolean changed = participants.findByOrganizationIdAndChannelAndExternalId(customer.organizationId(),
                customer.channel(), customer.externalId())
                .map(p -> p.applyProfile(name, pictureUrl, clock.instant())).orElse(false);
        if (changed) {
            publisher.publishEvent(new ParticipantProfileUpdatedEvent(customer.organizationId(),
                    customer.conversationId()));
        }
        return changed;
    }

    private Optional<Participant> customerOf(Conversation conversation) {
        return participants.findByOrganizationIdAndChannelAndExternalId(conversation.getOrganization().getId(),
                conversation.getChannel(), conversation.getExternalThreadId());
    }

    private static ProfileTarget toTarget(Conversation c) {
        return new ProfileTarget(c.getOrganization().getId(), c.getId(), c.getChannel(), c.getExternalThreadId(),
                c.getMetaPage() == null ? null : c.getMetaPage().getId(),
                c.getInstagramAccount() == null ? null : c.getInstagramAccount().getId());
    }
}
