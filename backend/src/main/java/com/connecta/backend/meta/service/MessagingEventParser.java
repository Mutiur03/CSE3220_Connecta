package com.connecta.backend.meta.service;

import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * The per-product parsing step of webhook intake: turns one payload entry's messaging events into
 * {@link NormalizedMessage}s. Messenger and Instagram send the same messaging shape, so one parser serves both;
 * the product is carried on each message. Events without a message id, or with neither text nor an attachment,
 * carry nothing to show and are skipped.
 */
@Component
public class MessagingEventParser {

    private static final String MESSAGING = "messaging";

    public List<NormalizedMessage> parse(WebhookObject object, JsonNode entry) {
        List<NormalizedMessage> messages = new ArrayList<>();
        for (JsonNode event : entry.path(MESSAGING)) {
            parseOne(object, event).ifPresent(messages::add);
        }
        return messages;
    }

    private static java.util.Optional<NormalizedMessage> parseOne(WebhookObject object, JsonNode event) {
        JsonNode message = event.path("message");
        String mid = message.path("mid").asText("");
        String text = textOf(message);
        if (mid.isBlank() || text == null) {
            return java.util.Optional.empty();
        }
        boolean echo = message.path("is_echo").asBoolean(false);
        // An echo is a message the business sent, so the customer is the recipient; otherwise the sender.
        String peerId = echo ? event.path("recipient").path("id").asText("") : event.path("sender").path("id").asText("");
        if (peerId.isBlank()) {
            return java.util.Optional.empty();
        }
        Instant sentAt = Instant.ofEpochMilli(event.path("timestamp").asLong(0));
        Direction direction = echo ? Direction.OUTBOUND : Direction.INBOUND;
        return java.util.Optional.of(new NormalizedMessage(object, mid, peerId, direction, text, sentAt));
    }

    /** The text, or a label for the first attachment, or null when there is neither. */
    private static String textOf(JsonNode message) {
        if (message.hasNonNull("text") && !message.path("text").asText().isBlank()) {
            return message.path("text").asText();
        }
        JsonNode attachments = message.path("attachments");
        if (attachments.isArray() && !attachments.isEmpty()) {
            return attachmentLabel(attachments.get(0).path("type").asText(""));
        }
        return null;
    }

    private static String attachmentLabel(String type) {
        return switch (type) {
            case "image" -> "[Photo]";
            case "video" -> "[Video]";
            case "audio" -> "[Voice message]";
            default -> "[Attachment]";
        };
    }
}
