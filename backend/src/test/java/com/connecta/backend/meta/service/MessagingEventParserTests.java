package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    class Attachments {

        private NormalizedMessage withAttachments(String attachmentsJson) {
            return parser.parse(WebhookObject.PAGE, entry("{\"sender\":{\"id\":\"customer-1\"},"
                    + "\"message\":{\"mid\":\"m.9\",\"attachments\":[" + attachmentsJson + "]}}")).get(0);
        }

        @ParameterizedTest
        @CsvSource({
            "image, IMAGE, [Photo]",
            "video, VIDEO, [Video]",
            "audio, AUDIO, [Voice message]",
            "file, FILE, [Attachment]"})
        void eachKnownTypeKeepsItsUrlAndLabelsTheText(String type, AttachmentKind kind, String label) {
            NormalizedMessage message = withAttachments(
                    "{\"type\":\"" + type + "\",\"payload\":{\"url\":\"https://cdn.example/a\"}}");

            assertEquals(label, message.text());
            assertEquals(1, message.attachments().size());
            assertEquals(kind, message.attachments().get(0).kind());
            assertEquals("https://cdn.example/a", message.attachments().get(0).url());
            assertNull(message.attachments().get(0).contentType());
        }

        @Test
        void everyAttachmentIsKeptInOrderWithItsNameWhenPresent() {
            NormalizedMessage message = withAttachments(
                    "{\"type\":\"image\",\"payload\":{\"url\":\"https://cdn.example/1.jpg\"}},"
                    + "{\"type\":\"file\",\"payload\":{\"url\":\"https://cdn.example/2\",\"name\":\"a.pdf\"}}");

            assertEquals("[Photo]", message.text());
            assertEquals(2, message.attachments().size());
            assertNull(message.attachments().get(0).fileName());
            assertEquals("a.pdf", message.attachments().get(1).fileName());
        }

        @Test
        void textIsKeptAlongsideAnAttachment() {
            NormalizedMessage message = parser.parse(WebhookObject.INSTAGRAM, entry(
                    "{\"sender\":{\"id\":\"ig-customer\"},\"message\":{\"mid\":\"m.2\",\"text\":\"Look\","
                    + "\"attachments\":[{\"type\":\"image\",\"payload\":{\"url\":\"https://cdn.example/p\"}}]}}"))
                    .get(0);

            assertEquals("Look", message.text());
            assertEquals(WebhookObject.INSTAGRAM, message.channel());
            assertEquals(AttachmentKind.IMAGE, message.attachments().get(0).kind());
        }

        @Test
        void anUnknownTypeKeepsOnlyTheGenericLabel() {
            NormalizedMessage message = withAttachments(
                    "{\"type\":\"location\",\"payload\":{\"url\":\"https://maps.example/x\"}}");

            assertEquals("[Attachment]", message.text());
            assertTrue(message.attachments().isEmpty());
        }

        @ParameterizedTest
        @CsvSource({
            "share, Shared post, [Shared post]",
            "ig_post, Shared post, [Shared post]",
            "ig_reel, Shared post, [Shared post]",
            "reel, Shared post, [Shared post]",
            "story_mention, Story mention, [Story mention]"})
        void aSharedPostOrStoryMentionBecomesATitledLink(String type, String title, String label) {
            NormalizedMessage message = withAttachments("{\"type\":\"" + type
                    + "\",\"payload\":{\"url\":\"https://www.instagram.com/p/abc/\"}}");

            assertEquals(label, message.text());
            assertEquals(1, message.attachments().size());
            assertEquals(AttachmentKind.LINK, message.attachments().get(0).kind());
            assertEquals("https://www.instagram.com/p/abc/", message.attachments().get(0).url());
            assertEquals(title, message.attachments().get(0).fileName());
            assertNull(message.attachments().get(0).contentType());
        }

        @Test
        void aShareWithoutAWebUrlKeepsOnlyTheGenericLabel() {
            NormalizedMessage message = withAttachments(
                    "{\"type\":\"share\",\"payload\":{\"url\":\"javascript:alert(1)\"}}");

            assertEquals("[Attachment]", message.text());
            assertTrue(message.attachments().isEmpty());
        }

        @Test
        void aReelKeepsItsPageUrlAndNeverItsCaption() {
            NormalizedMessage message = withAttachments("{\"type\":\"ig_reel\",\"payload\":{"
                    + "\"reel_video_id\":\"123\",\"title\":\"A long caption\","
                    + "\"url\":\"https://www.instagram.com/reel/abc/\"}}");

            assertEquals("[Shared post]", message.text());
            assertEquals(AttachmentKind.LINK, message.attachments().get(0).kind());
            assertEquals("https://www.instagram.com/reel/abc/", message.attachments().get(0).url());
            assertEquals("Shared post", message.attachments().get(0).fileName());
        }

        @Test
        void aReelWithoutAUrlKeepsOnlyTheGenericLabel() {
            NormalizedMessage message = withAttachments("{\"type\":\"ig_reel\",\"payload\":{\"title\":\"x\"}}");

            assertEquals("[Attachment]", message.text());
            assertTrue(message.attachments().isEmpty());
        }

        @Test
        void anAttachmentWithoutAUrlKeepsOnlyItsLabel() {
            NormalizedMessage message = withAttachments("{\"type\":\"image\"}");

            assertEquals("[Photo]", message.text());
            assertTrue(message.attachments().isEmpty());
        }

        @ParameterizedTest
        @CsvSource({"javascript:alert(1)", "ftp://cdn.example/a", "/relative/path", "https://"})
        void aUrlThatIsNotAnAbsoluteWebUrlIsIgnored(String url) {
            NormalizedMessage message = withAttachments(
                    "{\"type\":\"video\",\"payload\":{\"url\":\"" + url + "\"}}");

            assertEquals("[Video]", message.text());
            assertTrue(message.attachments().isEmpty());
        }

        @Test
        void aMalformedAttachmentListIsIgnored() {
            NormalizedMessage message = parser.parse(WebhookObject.PAGE, entry(
                    "{\"sender\":{\"id\":\"c\"},\"message\":{\"mid\":\"m.5\",\"text\":\"hi\","
                    + "\"attachments\":{\"type\":\"image\"}}}")).get(0);

            assertTrue(message.attachments().isEmpty());
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

        @Test
        void anEchoedReelIsStoredAsTheSameLink() {
            NormalizedMessage message = parser.parse(WebhookObject.INSTAGRAM, entry(
                    "{\"sender\":{\"id\":\"ig-1\"},\"recipient\":{\"id\":\"customer-1\"},"
                    + "\"message\":{\"mid\":\"m.4\",\"is_echo\":true,\"attachments\":[{\"type\":\"ig_reel\","
                    + "\"payload\":{\"url\":\"https://www.instagram.com/reel/abc/\"}}]}}")).get(0);

            assertEquals(Direction.OUTBOUND, message.direction());
            assertEquals("[Shared post]", message.text());
            assertEquals(AttachmentKind.LINK, message.attachments().get(0).kind());
            assertEquals("https://www.instagram.com/reel/abc/", message.attachments().get(0).url());
            assertEquals("Shared post", message.attachments().get(0).fileName());
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
