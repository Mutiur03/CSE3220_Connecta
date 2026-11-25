package com.connecta.backend.inbox.domain;

/** What a message attachment holds. The label is the message text when an attachment arrives without text. */
public enum AttachmentKind {
    IMAGE("[Photo]"),
    VIDEO("[Video]"),
    AUDIO("[Voice message]"),
    FILE("[Attachment]");

    private final String label;

    AttachmentKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
