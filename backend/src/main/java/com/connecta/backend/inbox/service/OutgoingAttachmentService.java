package com.connecta.backend.inbox.service;

import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.meta.service.AttachmentChannelClient;
import com.connecta.backend.meta.service.AttachmentChannelClientFactory;
import com.connecta.backend.meta.service.UnsupportedAttachmentException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Sends an agent's file through the conversation's channel (Adapter chosen by Factory), then stores the message.
 * Same shape as {@link OutgoingMessageService}: not transactional, so the Meta call holds no database connection.
 * The file is checked (size, type by declared type and magic bytes, channel support) before anything is stored,
 * then kept by {@link StoredFileService} so Meta can fetch it from its public URL. If Meta refuses, the stored
 * file is deleted again. The message is recorded with a label ({@code [Photo]} ...) and its attachment, with the
 * same webhook-echo dedupe as text. Any member (ADMIN or AGENT) may send.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutgoingAttachmentService {

    private final ConversationDetailService conversations;
    private final AttachmentValidator validator;
    private final AttachmentChannelClientFactory channels;
    private final StoredFileService files;
    private final OutgoingMessageRecorder recorder;

    public MessageResponse send(String subject, UUID organizationId, UUID conversationId, AttachmentUpload upload) {
        Conversation conversation = conversations.find(subject, organizationId, conversationId);
        AttachmentType type = validator.validate(upload.bytes(), upload.contentType());
        AttachmentChannelClient channel = channels.forChannel(conversation.getChannel());
        if (!channel.supports(type.kind())) {
            throw new UnsupportedAttachmentException();
        }
        String fileName = StoredFileService.safeFileName(upload.fileName());
        UUID fileId = files.store(organizationId, upload.bytes(), type.contentType(), fileName);
        OutgoingAttachment attachment;
        String externalId;
        try {
            attachment = new OutgoingAttachment(type.kind(), files.publicUrl(fileId), fileName, type.contentType());
            externalId = channel.sendAttachment(conversation, type.kind(), attachment.url());
        } catch (RuntimeException notSent) {
            deleteUnsent(organizationId, fileId, notSent);
            throw notSent;
        }
        return record(subject, organizationId, conversationId, externalId, attachment);
    }

    private MessageResponse record(String subject, UUID organizationId, UUID conversationId, String externalId,
            OutgoingAttachment attachment) {
        try {
            return recorder.recordAttachment(subject, organizationId, conversationId, externalId, attachment);
        } catch (DataIntegrityViolationException duplicate) {
            log.info("The webhook echo committed between the lookup and the save; attributing the stored row");
            return recorder.attributeStored(subject, organizationId, externalId, attachment)
                    .orElseThrow(() -> duplicate);
        }
    }

    /** Nothing was sent, so nothing may keep the file. A failed cleanup is attached to the original failure. */
    private void deleteUnsent(UUID organizationId, UUID fileId, RuntimeException notSent) {
        try {
            files.delete(organizationId, fileId);
        } catch (RuntimeException cleanup) {
            log.warn("Could not delete an unsent attachment file", cleanup);
            notSent.addSuppressed(cleanup);
        }
    }
}
