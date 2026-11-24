package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.ConversationParticipant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

    /** The customers of the given conversations, with the participant loaded in the same query. */
    @Query("select cp from ConversationParticipant cp join fetch cp.participant "
            + "where cp.conversation.id in :conversationIds order by cp.createdAt, cp.id")
    List<ConversationParticipant> findWithParticipantByConversationIdIn(
            @Param("conversationIds") Collection<UUID> conversationIds);
}
