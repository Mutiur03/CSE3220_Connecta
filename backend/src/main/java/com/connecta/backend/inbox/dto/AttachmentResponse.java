package com.connecta.backend.inbox.dto;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.MessageAttachment;
import java.util.UUID;

/** One attachment of a message as the API returns it. File name and content type are null when unknown. */
public record AttachmentResponse(UUID id, AttachmentKind kind, String url, String fileName, String contentType) {

    public static AttachmentResponse from(MessageAttachment attachment) {
        return new AttachmentResponse(attachment.getId(), attachment.getKind(), attachment.getUrl(),
                attachment.getFileName(), attachment.getContentType());
    }
}
