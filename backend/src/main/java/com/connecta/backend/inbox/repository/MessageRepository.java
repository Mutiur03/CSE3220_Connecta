package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Whether this organization already stored the provider message. Scoped by organization. */
    boolean existsByOrganizationIdAndExternalMessageId(UUID organizationId, String externalMessageId);

    /** The provider message, scoped by organization. */
    @EntityGraph(attributePaths = {"senderParticipant", "sentByUser"})
    Optional<Message> findByOrganizationIdAndExternalMessageId(UUID organizationId, String externalMessageId);

    /** Messages of one direction sent at exactly this instant, scoped by organization. */
    List<Message> findByOrganizationIdAndDirectionAndSentAt(UUID organizationId, MessageDirection direction,
            Instant sentAt);

    /** One page of a conversation's messages, with sender and agent loaded in the same query. */
    @EntityGraph(attributePaths = {"senderParticipant", "sentByUser"})
    Page<Message> findByConversationIdAndOrganizationId(UUID conversationId, UUID organizationId, Pageable pageable);

    /** One message with its sender loaded, scoped by organization. */
    @EntityGraph(attributePaths = {"senderParticipant", "sentByUser"})
    Optional<Message> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
