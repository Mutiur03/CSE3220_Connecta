package com.connecta.backend.inbox.service;

import com.connecta.backend.config.AttachmentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Checks an uploaded attachment before anything is stored or sent: not empty, within the limit, an allowed type. */
@Component
@RequiredArgsConstructor
public class AttachmentValidator {

    private final AttachmentProperties limits;

    /**
     * The file's type.
     *
     * @throws EmptyAttachmentException the file has no bytes
     * @throws AttachmentTooLargeException the file is larger than app.attachments.max-bytes
     * @throws UnsupportedAttachmentTypeException the declared type is not allowed or the bytes are not that type
     */
    public AttachmentType validate(byte[] bytes, String declaredContentType) {
        if (bytes.length == 0) {
            throw new EmptyAttachmentException();
        }
        if (bytes.length > limits.maxBytes()) {
            throw new AttachmentTooLargeException(limits.maxBytes());
        }
        return AttachmentType.detect(declaredContentType, bytes)
                .orElseThrow(UnsupportedAttachmentTypeException::new);
    }
}
