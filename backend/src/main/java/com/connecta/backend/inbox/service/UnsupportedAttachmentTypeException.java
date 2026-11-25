package com.connecta.backend.inbox.service;

/** The uploaded file is not an allowed type, or its content does not match the type it claims. */
public class UnsupportedAttachmentTypeException extends RuntimeException {

    public UnsupportedAttachmentTypeException() {
        super("This file type cannot be sent. Send a JPEG, PNG or GIF image, an MP4 video, MP3, M4A, AAC or WAV "
                + "audio, or a PDF.");
    }
}
