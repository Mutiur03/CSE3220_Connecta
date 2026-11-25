package com.connecta.backend.inbox.service;

/** A media download failed. The message is the reason only, never the URL. */
public class MediaDownloadException extends RuntimeException {

    private final MediaDownloadFailure reason;

    public MediaDownloadException(MediaDownloadFailure reason) {
        super(reason.name());
        this.reason = reason;
    }

    public MediaDownloadFailure reason() {
        return reason;
    }
}
