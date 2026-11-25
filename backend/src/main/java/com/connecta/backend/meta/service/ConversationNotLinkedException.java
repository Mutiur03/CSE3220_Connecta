package com.connecta.backend.meta.service;

/** The conversation records no connected account and the organization has several, so the sender is ambiguous. */
public class ConversationNotLinkedException extends RuntimeException {

    public ConversationNotLinkedException() {
        super("This conversation is not linked to a connected account, so the reply cannot be sent.");
    }
}
