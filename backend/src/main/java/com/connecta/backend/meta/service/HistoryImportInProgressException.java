package com.connecta.backend.meta.service;

/** An import for this account is already running on this server. */
public class HistoryImportInProgressException extends RuntimeException {

    public HistoryImportInProgressException() {
        super("An import for this account is already running. Try again when it finishes.");
    }
}
