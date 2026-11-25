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
 * kept; any other attachment only contributes the label used as text when the message has none.
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
            "ig_reel", AttachmentKind.VIDEO,
            "reel", AttachmentKind.VIDEO,
            "audio", AttachmentKind.AUDIO,
            "file", AttachmentKind.FILE);

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
        String text = textOf(message);
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
        return Optional.of(new NormalizedMessage(object, mid, peerId, direction, text, sentAt,
                attachmentsOf(message)));
    }

    /** The text, or a label for the first attachment, or null when there is neither. */
    private static String textOf(JsonNode message) {
        if (message.hasNonNull("text") && !message.path("text").asText().isBlank()) {
            return message.path("text").asText();
        }
        JsonNode attachments = message.path(ATTACHMENTS);
        if (attachments.isArray() && !attachments.isEmpty()) {
            return kindOf(attachments.get(0)).orElse(AttachmentKind.FILE).label();
        }
        return null;
    }

    private static List<NormalizedAttachment> attachmentsOf(JsonNode message) {
        JsonNode attachments = message.path(ATTACHMENTS);
        List<NormalizedAttachment> found = new ArrayList<>();
        if (!attachments.isArray()) {
            return found;
        }
        for (JsonNode attachment : attachments) {
            JsonNode payload = attachment.path("payload");
            String name = payload.path(NAME).asText(attachment.path(NAME).asText(""));
            kindOf(attachment)
                    .flatMap(kind -> NormalizedAttachment.of(kind, payload.path("url").asText(""), name, null))
                    .ifPresent(found::add);
        }
        return found;
    }

    private static Optional<AttachmentKind> kindOf(JsonNode attachment) {
        return Optional.ofNullable(KINDS.get(attachment.path("type").asText("")));
    }
}
