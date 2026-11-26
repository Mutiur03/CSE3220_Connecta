package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.AssignmentSource;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.repository.ConversationAssignmentRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Gives a conversation nobody has taken yet to the agent the organization strategy chooses. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoAssignmentService {

    private final ConversationRepository conversations;
    private final ConversationAssignmentRepository history;
    private final AssignmentStrategyFactory strategies;

    /** Own transaction: it runs after the message committed and must not touch that one. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assignIfUnassigned(UUID organizationId, UUID conversationId) {
        Conversation conversation = conversations.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(ConversationNotFoundException::new);
        if (conversation.getAssignee() != null) {
            return;
        }
        strategies.forOrganization(organizationId).choose(conversation).ifPresentOrElse(member -> {
            history.save(conversation.assignTo(member, null, AssignmentSource.AUTO_ROUND_ROBIN));
            log.debug("Conversation {} assigned to member {}", conversationId, member.getId());
        }, () -> log.debug("No available agent for conversation {}", conversationId));
    }
}
