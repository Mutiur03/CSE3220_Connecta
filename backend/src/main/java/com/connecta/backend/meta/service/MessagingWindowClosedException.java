package com.connecta.backend.meta.service;

/** Meta refused the message: the customer's 24 hour reply window is closed or a messaging policy forbids it. */
public class MessagingWindowClosedException extends RuntimeException {

    public MessagingWindowClosedException() {
        super("Meta does not allow a reply to this customer now (reply window closed or policy).");
    }
}
