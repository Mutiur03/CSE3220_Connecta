package com.connecta.backend.inbox.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.domain.AssignmentSource;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.dto.ConversationAssigneeResponse;
import com.connecta.backend.inbox.dto.ConversationAssignmentResponse;
import com.connecta.backend.inbox.repository.ConversationAssignmentRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.MemberService;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual assignment: an ADMIN of the organization hands a conversation to a member, to another member, or
 * back to nobody. Every hand-over is saved as history with source MANUAL. The target may be away: a manager
 * can override availability. Conversation and target are both looked up inside the organization, so another
 * tenant's ids read as not found. The history is readable by ADMIN only.
 */
@Service
@RequiredArgsConstructor
public class ConversationAssignmentService {

    /** Newest first, then by id so hand-overs in the same instant never swap places between pages. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ConversationRepository conversations;
    private final ConversationAssignmentRepository history;
    private final MemberService members;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher publisher;

    /** Assigns, reassigns or (null {@code membershipId}) unassigns. Asking for the current state is a 422. */
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Transactional
    public ConversationAssigneeResponse assign(String subject, UUID organizationId, UUID conversationId,
            UUID membershipId) {
        User caller = activeCaller.load(subject);
        tenants.requireRole(organizationId, caller.getId(), Roles.ADMIN);
        Conversation conversation = find(organizationId, conversationId);
        Membership target = membershipId == null ? null : members.findMember(organizationId, membershipId);
        requireChange(conversation.getAssignee(), target);
        if (target != null && !target.getUser().isEnabled()) {
            throw new AssigneeNotAvailableException();
        }
        history.save(conversation.assignTo(target, caller, AssignmentSource.MANUAL));
        ConversationAssigneeResponse result = ConversationAssigneeResponse.from(conversations.saveAndFlush(conversation));
        publisher.publishEvent(new AssignmentChangedEvent(organizationId, conversationId, membershipId));
        return result;
    }

    /** ADMIN of the organization. */
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Transactional(readOnly = true)
    public PageResponse<ConversationAssignmentResponse> list(String subject, UUID organizationId,
            UUID conversationId, int page, int size) {
        tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        find(organizationId, conversationId);
        return PageResponse.from(history.findByConversationIdAndOrganizationId(conversationId, organizationId,
                PageRequest.of(page, size, NEWEST_FIRST)), ConversationAssignmentResponse::from);
    }

    private Conversation find(UUID organizationId, UUID conversationId) {
        return conversations.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(ConversationNotFoundException::new);
    }

    private static void requireChange(Membership current, Membership target) {
        UUID currentId = current == null ? null : current.getId();
        UUID targetId = target == null ? null : target.getId();
        if (Objects.equals(currentId, targetId)) {
            throw new AssignmentUnchangedException();
        }
    }
}
