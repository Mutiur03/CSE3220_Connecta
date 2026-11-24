package com.connecta.backend.meta.service;

/** Meta could not deliver the message. The Graph error detail is logged, never returned. */
public class ChannelSendFailedException extends RuntimeException {

    public ChannelSendFailedException(String message) {
        super(message);
    }

    public ChannelSendFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
