package com.connecta.backend.meta.service;

import com.connecta.backend.meta.domain.WebhookObject;
import java.time.Instant;
import java.util.List;

/**
 * One Messenger or Instagram message in the common internal format, whatever the product's payload looked like.
 * It is not tied to an organization yet: webhook processing routes it to each connected organization.
 *
 * @param channel the product the message came through (PAGE is Messenger, INSTAGRAM is Instagram)
 * @param externalMessageId Meta's message id, the key that makes redelivery harmless
 * @param peerId the customer's Meta id (sender for inbound, recipient for outbound)
 * @param direction whether the customer sent it or the business did
 * @param text the message text, or a label for a non-text attachment
 * @param sentAt when Meta says it was sent
 * @param attachments the media sent with the message, in Meta's order; never null
 * @param disappearing true for a view-once photo Meta sent as type "ephemeral" (no media is ever given)
 */
public record NormalizedMessage(WebhookObject channel, String externalMessageId, String peerId, Direction direction,
        String text, Instant sentAt, List<NormalizedAttachment> attachments, boolean disappearing) {

    /** The body of a view-once photo: shown as a note, the photo itself is never stored. */
    public static final String DISAPPEARING_PHOTO = "[Disappearing photo]";

    public NormalizedMessage {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public NormalizedMessage(WebhookObject channel, String externalMessageId, String peerId, Direction direction,
            String text, Instant sentAt, List<NormalizedAttachment> attachments) {
        this(channel, externalMessageId, peerId, direction, text, sentAt, attachments, false);
    }

    /** The same message as a view-once photo: label as text, no attachment. */
    public NormalizedMessage asDisappearing() {
        return new NormalizedMessage(channel, externalMessageId, peerId, direction, DISAPPEARING_PHOTO, sentAt,
                List.of(), true);
    }

    /** True when this is the sender's echo of a view-once photo. */
    public boolean isDisappearingEcho() {
        return disappearing && direction == Direction.OUTBOUND;
    }

    public enum Direction {
        INBOUND,
        OUTBOUND
    }
}
