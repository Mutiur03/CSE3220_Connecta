package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaProcessedMessage;
import com.connecta.backend.meta.domain.WebhookObject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaProcessedMessageRepository extends JpaRepository<MetaProcessedMessage, UUID> {

    /** Whether this organization has already received the message. Scoped by organization. */
    boolean existsByOrganizationIdAndObjectAndExternalMessageId(UUID organizationId, WebhookObject object,
            String externalMessageId);
}
