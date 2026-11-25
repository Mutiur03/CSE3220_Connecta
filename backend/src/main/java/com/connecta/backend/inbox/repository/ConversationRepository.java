package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository
        extends JpaRepository<Conversation, UUID>, JpaSpecificationExecutor<Conversation> {

    /** Scoped by organization: another tenant's conversation id finds nothing. */
    Optional<Conversation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Conversation> findByOrganizationIdAndChannelAndExternalThreadId(UUID organizationId, Channel channel,
            String externalThreadId);

    /** Conversations whose customer was never looked up on Meta, newest first (feeds the post-import refresh). */
    @Query("""
            select c from Conversation c
            where c.organization.id = :organizationId
              and exists (select 1 from Participant p
                          where p.organization.id = :organizationId and p.channel = c.channel
                            and p.externalId = c.externalThreadId and p.profilePictureFetchedAt is null)
            order by c.lastMessageAt desc nulls last, c.id""")
    List<Conversation> findWithUnfetchedCustomer(@Param("organizationId") UUID organizationId, Pageable page);

    /** Same as above across all organizations (startup catch-up); each row carries its own organization. */
    @Query("""
            select c from Conversation c
            where exists (select 1 from Participant p
                          where p.organization.id = c.organization.id and p.channel = c.channel
                            and p.externalId = c.externalThreadId and p.profilePictureFetchedAt is null)
            order by c.lastMessageAt desc nulls last, c.id""")
    List<Conversation> findAllWithUnfetchedCustomer(Pageable page);

    /** The newest message of each given conversation (several rows only when two share the same sent time). */
    @Query("""
            select m from Message m
            where m.conversation.id in :conversationIds
              and m.sentAt = (select max(m2.sentAt) from Message m2 where m2.conversation = m.conversation)
            order by m.id""")
    List<Message> findLastMessages(@Param("conversationIds") Collection<UUID> conversationIds);
}
