package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.ConversationAssignment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationAssignmentRepository extends JpaRepository<ConversationAssignment, UUID> {

    /** One page of a conversation's assignment history, scoped by organization. The pageable carries the sort. */
    @EntityGraph(attributePaths = {"fromMember.user", "toMember.user", "assignedBy"})
    Page<ConversationAssignment> findByConversationIdAndOrganizationId(UUID conversationId, UUID organizationId,
            Pageable pageable);
}
