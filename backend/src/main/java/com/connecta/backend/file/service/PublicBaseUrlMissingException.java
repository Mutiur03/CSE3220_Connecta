package com.connecta.backend.file.service;

/** PUBLIC_BASE_URL is not set, so a stored file has no address an outside system could fetch. */
public class PublicBaseUrlMissingException extends RuntimeException {

    public PublicBaseUrlMissingException() {
        super("Attachments cannot be sent: the server's public address (PUBLIC_BASE_URL) is not configured.");
    }
}
