package com.connecta.backend.meta.service;

/** Meta refused to share conversations: the permission is missing or the token no longer works. */
public class HistoryPermissionException extends RuntimeException {

    public HistoryPermissionException() {
        super("Meta did not allow reading conversations. Reconnect the account and accept the messaging permissions.");
    }
}
