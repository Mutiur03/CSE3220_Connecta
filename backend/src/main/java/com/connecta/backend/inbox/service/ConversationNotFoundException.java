package com.connecta.backend.inbox.service;

/** No conversation with this id in the organization: unknown, or another organization's. */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException() {
        super("Conversation not found");
    }
}
