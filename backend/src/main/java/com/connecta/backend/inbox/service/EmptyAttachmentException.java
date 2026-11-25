package com.connecta.backend.inbox.service;

/** The uploaded attachment has no bytes. */
public class EmptyAttachmentException extends RuntimeException {

    public EmptyAttachmentException() {
        super("The file is empty.");
    }
}
