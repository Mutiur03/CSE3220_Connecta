package com.connecta.backend.inbox.service;

import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.meta.service.NormalizedMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a photo already stored into the {@code [Disappearing photo]} note: the body is replaced, its attachment
 * rows and their stored files are deleted. Scoped to one organization and to received messages with an image.
 */
@Service
@RequiredArgsConstructor
public class DisappearingPhotoService {

    private final MessageRepository messages;
    private final MessageAttachmentRepository attachments;
    private final StoredFileService files;

    /** Returns how many messages were converted. */
    @Transactional
    public int convert(UUID organizationId, Instant sentAt) {
        int converted = 0;
        for (Message message : messages.findByOrganizationIdAndDirectionAndSentAt(organizationId,
                MessageDirection.INBOUND, sentAt)) {
            List<MessageAttachment> held = attachments.findByMessage(organizationId, List.of(message.getId()))
                    .getOrDefault(message.getId(), List.of());
            if (held.stream().anyMatch(a -> a.getKind() == AttachmentKind.IMAGE)) {
                held.forEach(a -> discard(organizationId, a));
                message.correctBody(NormalizedMessage.DISAPPEARING_PHOTO);
                converted++;
            }
        }
        return converted;
    }

    private void discard(UUID organizationId, MessageAttachment attachment) {
        attachments.delete(attachment);
        if (attachment.getStoredFileId() != null) {
            files.delete(organizationId, attachment.getStoredFileId());
        }
    }
}
