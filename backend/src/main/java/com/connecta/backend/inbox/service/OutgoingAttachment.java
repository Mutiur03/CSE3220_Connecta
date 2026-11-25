package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.AttachmentKind;

/** The attachment of a sent message, as the recorder stores it: its kind, public URL, name and content type. */
public record OutgoingAttachment(AttachmentKind kind, String url, String fileName, String contentType) {
}
