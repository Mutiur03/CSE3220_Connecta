package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository
        extends JpaRepository<Conversation, UUID>, JpaSpecificationExecutor<Conversation> {

    Optional<Conversation> findByOrganizationIdAndChannelAndExternalThreadId(UUID organizationId, Channel channel,
            String externalThreadId);

    /** The newest message of each given conversation (several rows only when two share the same sent time). */
    @Query("""
            select m from Message m
            where m.conversation.id in :conversationIds
              and m.sentAt = (select max(m2.sentAt) from Message m2 where m2.conversation = m.conversation)
            order by m.id""")
    List<Message> findLastMessages(@Param("conversationIds") Collection<UUID> conversationIds);
}
