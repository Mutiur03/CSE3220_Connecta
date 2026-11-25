package com.connecta.backend.meta.service;

/** The conversation's channel cannot deliver this kind of attachment (for example a PDF on Instagram). */
public class UnsupportedAttachmentException extends RuntimeException {

    public UnsupportedAttachmentException() {
        super("This channel cannot deliver this kind of file. Send a photo, video or audio instead.");
    }
}
