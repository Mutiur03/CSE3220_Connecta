package com.connecta.backend.inbox.repository;

import com.connecta.backend.inbox.domain.MessageAttachment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** The message's attachments from Meta that have no copy yet. Scoped by organization. */
    @Query("select a from MessageAttachment a where a.organization.id = :organizationId"
            + " and a.message.id = :messageId and a.sourceUrl is not null and a.storedFileId is null")
    List<MessageAttachment> findMirrorPendingOfMessage(@Param("organizationId") UUID organizationId,
            @Param("messageId") UUID messageId);

    /**
     * Attachments from Meta without a copy, created after {@code since}, not tried since {@code retryBefore};
     * never tried first, then least recently tried. Every organization (the retry sweep).
     */
    @Query("select a from MessageAttachment a where a.sourceUrl is not null and a.storedFileId is null"
            + " and a.createdAt > :since and (a.mirrorAttemptedAt is null or a.mirrorAttemptedAt < :retryBefore)"
            + " order by a.mirrorAttemptedAt asc nulls first, a.createdAt asc")
    List<MessageAttachment> findMirrorPending(@Param("since") Instant since,
            @Param("retryBefore") Instant retryBefore, Limit limit);

    /** {@link #findMirrorPending} for one organization (after its history import). */
    @Query("select a from MessageAttachment a where a.organization.id = :organizationId"
            + " and a.sourceUrl is not null and a.storedFileId is null"
            + " and a.createdAt > :since and (a.mirrorAttemptedAt is null or a.mirrorAttemptedAt < :retryBefore)"
            + " order by a.mirrorAttemptedAt asc nulls first, a.createdAt asc")
    List<MessageAttachment> findMirrorPending(@Param("organizationId") UUID organizationId,
            @Param("since") Instant since, @Param("retryBefore") Instant retryBefore, Limit limit);

    /**
     * Claims one copy attempt atomically: sets the attempt time only when there is no copy and nobody tried
     * since {@code retryBefore}. Returns 1 for the one worker that may download, 0 for every other.
     */
    @Modifying
    @Query("update MessageAttachment a set a.mirrorAttemptedAt = :now where a.id = :id"
            + " and a.organization.id = :organizationId and a.storedFileId is null"
            + " and (a.mirrorAttemptedAt is null or a.mirrorAttemptedAt < :retryBefore)")
    int claimMirror(@Param("id") UUID id, @Param("organizationId") UUID organizationId, @Param("now") Instant now,
            @Param("retryBefore") Instant retryBefore);

    /** The attachment, row-locked until the transaction ends, so two copies can never both be saved. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MessageAttachment a where a.id = :id and a.organization.id = :organizationId")
    Optional<MessageAttachment> findForMirrorUpdate(@Param("id") UUID id,
            @Param("organizationId") UUID organizationId);
}
