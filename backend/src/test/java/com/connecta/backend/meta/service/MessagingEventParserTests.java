package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MessagingEventParserTests {

    private final MessagingEventParser parser = new MessagingEventParser();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode entry(String messagingJson) {
        return json.readTree("{\"id\":\"page-1\",\"messaging\":[" + messagingJson + "]}");
    }

    @Nested
    class Inbound {

        @Test
        void aTextMessageFromTheCustomerIsNormalized() {
            List<NormalizedMessage> messages = parser.parse(WebhookObject.PAGE, entry(
                    "{\"sender\":{\"id\":\"customer-1\"},\"recipient\":{\"id\":\"page-1\"},\"timestamp\":1700000000000,"
                    + "\"message\":{\"mid\":\"m.1\",\"text\":\"Hello\"}}"));

            assertEquals(1, messages.size());
            NormalizedMessage message = messages.get(0);
            assertEquals(WebhookObject.PAGE, message.channel());
            assertEquals("m.1", message.externalMessageId());
            assertEquals("customer-1", message.peerId());
            assertEquals(Direction.INBOUND, message.direction());
            assertEquals("Hello", message.text());
            assertEquals(Instant.ofEpochMilli(1700000000000L), message.sentAt());
        }

        @Test
        void anImageWithoutTextIsLabelled() {
            List<NormalizedMessage> messages = parser.parse(WebhookObject.INSTAGRAM, entry(
                    "{\"sender\":{\"id\":\"ig-customer\"},\"message\":{\"mid\":\"m.2\","
                    + "\"attachments\":[{\"type\":\"image\"}]}}"));

            assertEquals("[Photo]", messages.get(0).text());
            assertEquals(WebhookObject.INSTAGRAM, messages.get(0).channel());
        }
    }

    @Nested
    class Outbound {

        @Test
        void anEchoIsOutboundAndNamesTheCustomerAsPeer() {
            List<NormalizedMessage> messages = parser.parse(WebhookObject.PAGE, entry(
                    "{\"sender\":{\"id\":\"page-1\"},\"recipient\":{\"id\":\"customer-1\"},"
                    + "\"message\":{\"mid\":\"m.3\",\"is_echo\":true,\"text\":\"Hi there\"}}"));

            assertEquals(Direction.OUTBOUND, messages.get(0).direction());
            assertEquals("customer-1", messages.get(0).peerId());
        }
    }

    @Nested
    class Skipped {

        @Test
        void aMessageWithoutAnIdIsSkipped() {
            assertTrue(parser.parse(WebhookObject.PAGE, entry(
                    "{\"sender\":{\"id\":\"c\"},\"message\":{\"text\":\"no id\"}}")).isEmpty());
        }

        @Test
        void aMessageWithNeitherTextNorAttachmentIsSkipped() {
            assertTrue(parser.parse(WebhookObject.PAGE, entry(
                    "{\"sender\":{\"id\":\"c\"},\"message\":{\"mid\":\"m.4\"}}")).isEmpty());
        }

        @Test
        void aDeliveryWithoutMessagingIsEmpty() {
            assertTrue(parser.parse(WebhookObject.PAGE, json.readTree("{\"id\":\"page-1\",\"changes\":[]}")).isEmpty());
        }
    }
}
