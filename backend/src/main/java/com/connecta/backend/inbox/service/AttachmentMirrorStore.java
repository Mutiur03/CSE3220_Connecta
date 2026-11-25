package com.connecta.backend.inbox.service;

import com.connecta.backend.config.AttachmentMirrorProperties;
import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database side of copying incoming media, in short transactions so no connection is held during a download.
 * Each copy is claimed first (one atomic update, so only one worker downloads) and saved under a row lock that
 * re-checks for an existing copy, so two workers or a replay never store two files. The writes always run in
 * their own transaction (REQUIRES_NEW), also when called from an after-commit callback on the same thread.
 * Every write is scoped to the attachment's own organization, and the file is stored for that organization.
 */
@Service
@RequiredArgsConstructor
public class AttachmentMirrorStore {

    private final MessageAttachmentRepository attachments;
    private final StoredFileService files;
    private final AttachmentMirrorProperties settings;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MirrorCandidate> pendingOfMessage(UUID organizationId, UUID messageId) {
        return toCandidates(attachments.findMirrorPendingOfMessage(organizationId, messageId));
    }

    /** A batch of every organization's attachments due for a (re)try, never tried and least recently tried first. */
    @Transactional(readOnly = true)
    public List<MirrorCandidate> pending() {
        Instant now = clock.instant();
        return toCandidates(attachments.findMirrorPending(now.minus(settings.mirrorMaxAge()),
                now.minus(settings.mirrorRetryAfter()), Limit.of(settings.mirrorBatchSize())));
    }

    /** {@link #pending()} for one organization. */
    @Transactional(readOnly = true)
    public List<MirrorCandidate> pending(UUID organizationId) {
        Instant now = clock.instant();
        return toCandidates(attachments.findMirrorPending(organizationId, now.minus(settings.mirrorMaxAge()),
                now.minus(settings.mirrorRetryAfter()), Limit.of(settings.mirrorBatchSize())));
    }

    /** True for the one caller that may download now; false when copied or tried by someone else recently. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(MirrorCandidate candidate) {
        Instant now = clock.instant();
        return attachments.claimMirror(candidate.attachmentId(), candidate.organizationId(), now,
                now.minus(settings.mirrorRetryAfter())) == 1;
    }

    /**
     * Stores the copy and points the attachment at it, then announces it. False, and nothing stored, when the
     * attachment is gone or already has a copy.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveCopy(MirrorCandidate candidate, byte[] bytes, String contentType) {
        MessageAttachment attachment = attachments
                .findForMirrorUpdate(candidate.attachmentId(), candidate.organizationId()).orElse(null);
        if (attachment == null || attachment.isMirrored()) {
            return false;
        }
        UUID fileId = files.store(candidate.organizationId(), bytes, contentType, candidate.fileName());
        attachment.mirrored(fileId, files.publicUrl(fileId));
        publisher.publishEvent(new AttachmentMirroredEvent(candidate.organizationId(),
                attachment.getMessage().getConversation().getId()));
        return true;
    }

    private static List<MirrorCandidate> toCandidates(List<MessageAttachment> found) {
        return found.stream().map(MirrorCandidate::from).toList();
    }
}
