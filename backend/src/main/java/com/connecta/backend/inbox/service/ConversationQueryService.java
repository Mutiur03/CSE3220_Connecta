package com.connecta.backend.inbox.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.dto.ConversationSummaryResponse;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.ConversationSpecifications;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.PresenceService;
import com.connecta.backend.org.service.TenantAccessService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inbox list of one organization. Any member (ADMIN or AGENT) reads; a non-member or an unknown
 * organization reads as not found. The page is one query; customers and last messages are then loaded for the
 * whole page in one query each, so the list never costs one query per row.
 */
@Service
@RequiredArgsConstructor
public class ConversationQueryService {

    /** Most recent message first, conversations without messages last, ties broken by id. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("lastMessageAt").nullsLast(),
            Sort.Order.asc("id"));

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository links;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final PresenceService presence;

    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryResponse> list(String subject, UUID organizationId,
            ConversationFilter filter, int page, int size) {
        Membership caller = tenants.requireMembership(organizationId, activeCaller.load(subject).getId());
        Page<Conversation> found = conversations.findAll(toSpecification(organizationId, filter, caller.getId()),
                PageRequest.of(page, Math.min(size, PageResponse.MAX_SIZE), NEWEST_FIRST));
        List<UUID> ids = found.getContent().stream().map(Conversation::getId).toList();
        if (ids.isEmpty()) {
            return PageResponse.from(found, c -> null);
        }
        Map<UUID, Participant> customers = firstCustomers(ids);
        Map<UUID, Message> lastMessages = lastMessages(ids);
        return PageResponse.from(found, c -> ConversationSummaryResponse.from(c, customers.get(c.getId()),
                lastMessages.get(c.getId()), isAssigneeOnline(organizationId, c)));
    }

    /** Presence is an in-memory lookup, so this costs no query per row. */
    private boolean isAssigneeOnline(UUID organizationId, Conversation conversation) {
        Membership assignee = conversation.getAssignee();
        return assignee != null && presence.isOnline(organizationId, assignee.getUser().getId());
    }

    private Map<UUID, Participant> firstCustomers(List<UUID> ids) {
        Map<UUID, Participant> byConversation = new HashMap<>();
        for (ConversationParticipant link : links.findWithParticipantByConversationIdIn(ids)) {
            byConversation.putIfAbsent(link.getConversation().getId(), link.getParticipant());
        }
        return byConversation;
    }

    private Map<UUID, Message> lastMessages(List<UUID> ids) {
        Map<UUID, Message> byConversation = new HashMap<>();
        for (Message message : conversations.findLastMessages(ids)) {
            byConversation.putIfAbsent(message.getConversation().getId(), message);
        }
        return byConversation;
    }

    private static Specification<Conversation> toSpecification(UUID organizationId, ConversationFilter filter,
            UUID callerMembershipId) {
        Specification<Conversation> spec = ConversationSpecifications.inOrganization(organizationId)
                .and(ConversationSpecifications.fetchingAssignee());
        if (filter.assignee() == AssigneeScope.ME) {
            spec = spec.and(ConversationSpecifications.assignedTo(callerMembershipId));
        } else if (filter.assignee() == AssigneeScope.NONE) {
            spec = spec.and(ConversationSpecifications.unassigned());
        }
        if (filter.channel() != null) {
            spec = spec.and(ConversationSpecifications.onChannel(filter.channel()));
        }
        if (filter.status() != null) {
            spec = spec.and(ConversationSpecifications.withStatus(filter.status()));
        }
        if (filter.participantName() != null && !filter.participantName().isBlank()) {
            spec = spec.and(ConversationSpecifications.participantNameContains(filter.participantName().strip()));
        }
        return spec;
    }
}
