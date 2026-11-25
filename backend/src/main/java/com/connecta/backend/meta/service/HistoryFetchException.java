package com.connecta.backend.meta.service;

/** Meta could not return the conversation history. The Graph detail is logged, never returned. */
public class HistoryFetchException extends RuntimeException {

    public HistoryFetchException(String message, Throwable cause) {
        super(message, cause);
    }

    public HistoryFetchException(String message) {
        super(message);
    }
}
