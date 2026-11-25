package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.MessageAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    /** The attachments of the given messages in saved order, scoped by organization. */
    List<MessageAttachment> findByOrganizationIdAndMessageIdInOrderByCreatedAtAscIdAsc(UUID organizationId,
            Collection<UUID> messageIds);

    /** The attachments of the given messages, grouped by message id, in one query. Scoped by organization. */
    default Map<UUID, List<MessageAttachment>> findByMessage(UUID organizationId, Collection<UUID> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return findByOrganizationIdAndMessageIdInOrderByCreatedAtAscIdAsc(organizationId, messageIds).stream()
                .collect(Collectors.groupingBy(attachment -> attachment.getMessage().getId()));
    }
}
