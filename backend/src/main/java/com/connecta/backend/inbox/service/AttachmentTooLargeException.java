package com.connecta.backend.inbox.service;

/** The uploaded attachment is larger than the configured limit. */
public class AttachmentTooLargeException extends RuntimeException {

    private static final long MEGABYTE = 1024L * 1024;

    public AttachmentTooLargeException(long maxBytes) {
        super("The file is larger than the " + readable(maxBytes) + " limit.");
    }

    private static String readable(long bytes) {
        return bytes % MEGABYTE == 0 ? bytes / MEGABYTE + " MB" : bytes + " byte";
    }
}
