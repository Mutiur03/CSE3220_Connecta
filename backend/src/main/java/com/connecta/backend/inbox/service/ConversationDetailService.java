package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationStatus;
import com.connecta.backend.inbox.dto.ConversationDetailResponse;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates one conversation. Any member of the organization (ADMIN or AGENT) may do both. Every
 * lookup is scoped by organization, so another tenant's conversation id reads as not found.
 */
@Service
@RequiredArgsConstructor
public class ConversationDetailService {

    private final ConversationRepository conversations;
    private final ConversationParticipantRepository participants;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ConversationDetailResponse get(String subject, UUID organizationId, UUID conversationId) {
        return detail(find(subject, organizationId, conversationId));
    }

    /**
     * Any status may move to any other, so a closed conversation can be reopened. Closing records the caller
     * and the time for the agent reports.
     */
    @Transactional
    public ConversationDetailResponse updateStatus(String subject, UUID organizationId, UUID conversationId,
            ConversationStatus status) {
        User caller = activeCaller.load(subject);
        Conversation conversation = find(caller, organizationId, conversationId);
        conversation.changeStatus(status, caller, clock.instant());
        return detail(conversations.saveAndFlush(conversation));
    }

    /** Marks the conversation read or unread for the whole team. Any member may. */
    @Transactional
    public void updateReadState(String subject, UUID organizationId, UUID conversationId, boolean unread) {
        Conversation conversation = find(subject, organizationId, conversationId);
        conversation.changeReadState(unread);
        conversations.saveAndFlush(conversation);
    }

    /**
     * The conversation, when the caller is a member of the organization and the conversation belongs to it.
     * Other features (for example sending a reply) load a conversation through this method.
     */
    @Transactional(readOnly = true)
    public Conversation find(String subject, UUID organizationId, UUID conversationId) {
        return find(activeCaller.load(subject), organizationId, conversationId);
    }

    private Conversation find(User caller, UUID organizationId, UUID conversationId) {
        tenants.requireMembership(organizationId, caller.getId());
        return conversations.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(ConversationNotFoundException::new);
    }

    private ConversationDetailResponse detail(Conversation conversation) {
        return ConversationDetailResponse.from(conversation, participants
                .findByConversationIdAndOrganizationId(conversation.getId(), conversation.getOrganization().getId()));
    }
}
