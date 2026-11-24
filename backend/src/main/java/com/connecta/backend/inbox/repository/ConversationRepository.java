package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByOrganizationIdAndChannelAndExternalThreadId(UUID organizationId, Channel channel,
            String externalThreadId);
}
