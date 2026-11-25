package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The per-product parsing step of webhook intake: turns one payload entry's messaging events into
 * {@link NormalizedMessage}s. Messenger and Instagram send the same messaging shape, so one parser serves both;
 * the product is carried on each message. Events without a message id, or with neither text nor an attachment,
 * carry nothing to show and are skipped. Each attachment of a known type with an http(s) {@code payload.url} is
 * kept: media by its kind, an Instagram shared post ({@code ig_post}, its image), a shared reel or post link
 * ({@code share}, {@code ig_reel}, {@code reel}) or {@code story_mention} as a LINK titled for what it is.
 * Any other attachment only contributes the label used as text when the message has none.
 */
@Component
public class MessagingEventParser {

    private static final String MESSAGING = "messaging";
    private static final String ATTACHMENTS = "attachments";
    private static final String NAME = "name";

    /** Meta's attachment types (Messenger and Instagram) that carry a media URL, by what they hold. */
    private static final Map<String, AttachmentKind> KINDS = Map.of(
            "image", AttachmentKind.IMAGE,
            "video", AttachmentKind.VIDEO,
            "audio", AttachmentKind.AUDIO,
            "file", AttachmentKind.FILE);

    /**
     * Instagram's attachment types that point at a post, reel or story rather than media, by their title. A reel's
     * {@code payload.url} is an instagram.com page, not a video file, so it is a link too; the caption is never used.
     */
    private static final Map<String, String> LINK_TITLES = Map.of(
            "share", NormalizedAttachment.SHARED_POST,
            "ig_reel", NormalizedAttachment.SHARED_POST,
            "reel", NormalizedAttachment.SHARED_POST,
            "story_mention", "Story mention");

    /**
     * Instagram's attachment types that are a picture under another type name, by their title. A regular post's
     * {@code payload.url} is a signed CDN image (Instagram sends no post permalink), so it is an IMAGE: the mirror
     * copies it before the signature expires. The caption ({@code title}) is never used.
     */
    private static final Map<String, String> IMAGE_TITLES = Map.of("ig_post", NormalizedAttachment.SHARED_POST);

    /** Instagram's view-once photo: the type carries no media, so it becomes a label. */
    private static final Map<String, String> EPHEMERAL_LABELS = Map.of("ephemeral",
            NormalizedMessage.DISAPPEARING_PHOTO);

    public List<NormalizedMessage> parse(WebhookObject object, JsonNode entry) {
        List<NormalizedMessage> messages = new ArrayList<>();
        for (JsonNode event : entry.path(MESSAGING)) {
            parseOne(object, event).ifPresent(messages::add);
        }
        return messages;
    }

    private static Optional<NormalizedMessage> parseOne(WebhookObject object, JsonNode event) {
        JsonNode message = event.path("message");
        String mid = message.path("mid").asText("");
        List<NormalizedAttachment> attachments = attachmentsOf(message);
        String text = textOf(message, attachments);
        boolean disappearing = isEphemeral(message);
        if (mid.isBlank() || text == null) {
            return Optional.empty();
        }
        boolean echo = message.path("is_echo").asBoolean(false);
        // An echo is a message the business sent, so the customer is the recipient; otherwise the sender.
        String peerId = echo ? event.path("recipient").path("id").asText("") : event.path("sender").path("id").asText("");
        if (peerId.isBlank()) {
            return Optional.empty();
        }
        Instant sentAt = Instant.ofEpochMilli(event.path("timestamp").asLong(0));
        Direction direction = echo ? Direction.OUTBOUND : Direction.INBOUND;
        return Optional.of(new NormalizedMessage(object, mid, peerId, direction, text, sentAt, attachments,
                disappearing));
    }

    /** The text, or the label of the first kept attachment, or of the first sent one, or null when there is none. */
    private static String textOf(JsonNode message, List<NormalizedAttachment> kept) {
        if (message.hasNonNull("text") && !message.path("text").asText().isBlank()) {
            return message.path("text").asText();
        }
        if (!kept.isEmpty()) {
            return kept.get(0).label();
        }
        JsonNode attachments = message.path(ATTACHMENTS);
        if (isEphemeral(message)) {
            return EPHEMERAL_LABELS.get(attachments.get(0).path("type").asText(""));
        }
        if (attachments.isArray() && !attachments.isEmpty()) {
            return kindOf(attachments.get(0)).orElse(AttachmentKind.FILE).label();
        }
        return null;
    }

    private static boolean isEphemeral(JsonNode message) {
        JsonNode attachments = message.path(ATTACHMENTS);
        return attachments.isArray() && !attachments.isEmpty()
                && EPHEMERAL_LABELS.containsKey(attachments.get(0).path("type").asText(""));
    }

    private static List<NormalizedAttachment> attachmentsOf(JsonNode message) {
        JsonNode attachments = message.path(ATTACHMENTS);
        List<NormalizedAttachment> found = new ArrayList<>();
        if (!attachments.isArray()) {
            return found;
        }
        for (JsonNode attachment : attachments) {
            toAttachment(attachment).ifPresent(found::add);
        }
        return found;
    }

    private static Optional<NormalizedAttachment> toAttachment(JsonNode attachment) {
        JsonNode payload = attachment.path("payload");
        String url = payload.path("url").asText("");
        String type = attachment.path("type").asText("");
        String imageTitle = IMAGE_TITLES.get(type);
        if (imageTitle != null) {
            return NormalizedAttachment.of(AttachmentKind.IMAGE, url, imageTitle, null);
        }
        String linkTitle = LINK_TITLES.get(type);
        if (linkTitle != null) {
            return NormalizedAttachment.link(url, linkTitle);
        }
        String name = payload.path(NAME).asText(attachment.path(NAME).asText(""));
        return kindOf(attachment).flatMap(kind -> NormalizedAttachment.of(kind, url, name, null));
    }

    private static Optional<AttachmentKind> kindOf(JsonNode attachment) {
        return Optional.ofNullable(KINDS.get(attachment.path("type").asText("")));
    }
}
