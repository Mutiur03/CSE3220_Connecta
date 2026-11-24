package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.Message;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    /** Whether this organization already stored the provider message. Scoped by organization. */
    boolean existsByOrganizationIdAndExternalMessageId(UUID organizationId, String externalMessageId);
}
