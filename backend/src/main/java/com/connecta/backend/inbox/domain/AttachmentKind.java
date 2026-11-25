package com.connecta.backend.inbox.domain;

/**
 * What a message attachment holds. The label is the message text when an attachment arrives without text.
 * A LINK points at a web page (an Instagram post, reel or story the customer shared), not at media, so it is
 * never downloaded or sent; its message text follows its title (see NormalizedAttachment#label).
 */
public enum AttachmentKind {
    IMAGE("[Photo]"),
    VIDEO("[Video]"),
    AUDIO("[Voice message]"),
    FILE("[Attachment]"),
    LINK("[Shared post]");

    private final String label;

    AttachmentKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
