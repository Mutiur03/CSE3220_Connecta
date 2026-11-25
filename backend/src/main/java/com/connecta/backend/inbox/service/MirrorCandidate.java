package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.MessageAttachment;
import java.util.UUID;

/** An attachment from Meta that has no copy yet: what the mirror needs outside a transaction. */
public record MirrorCandidate(UUID attachmentId, UUID organizationId, AttachmentKind kind, String sourceUrl,
        String fileName) {

    static MirrorCandidate from(MessageAttachment attachment) {
        return new MirrorCandidate(attachment.getId(), attachment.getOrganization().getId(), attachment.getKind(),
                attachment.getSourceUrl(), attachment.getFileName());
    }
}
