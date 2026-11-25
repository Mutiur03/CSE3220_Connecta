package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MessengerHistoryClientTests {

    private static final String BASE = "https://graph.facebook.com/v27.0/page-1/conversations";
    private static final MetaProperties META = new MetaProperties("app", "secret", "v27.0", "config",
            "https://x", "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=");

    private MockRestServiceServer server;
    private MessengerHistoryClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MessengerHistoryClient(META, HistoryImportProperties.defaults(), builder);
    }

    private static List<HistoryConversation> drain(Iterator<HistoryConversation> it) {
        List<HistoryConversation> all = new ArrayList<>();
        it.forEachRemaining(all::add);
        return all;
    }

    private static final String ONE_CONVERSATION = """
            {"data":[{"id":"t_1","participants":{"data":[
               {"id":"page-1","name":"Shop"},{"id":"cust-1","name":"Rina Akter"}]},
             "messages":{"data":[
               {"id":"m3","message":"Thanks","from":{"id":"page-1","name":"Shop"},"created_time":"2026-11-20T10:02:00+0000"},
               {"id":"m2","message":"","from":{"id":"cust-1","name":"Rina Akter"},"created_time":"2026-11-20T10:01:00+0000"},
               {"id":"m1","message":"Hi","from":{"id":"cust-1","name":"Rina Akter"},"created_time":"2026-11-20T10:00:00+0000"}]}}]}
            """;

    @Nested
    class Request {

        @Test
        void asksTheConversationsEdgeWithBearerTokenFieldsAndLimits() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                    .andExpect(method(HttpMethod.GET))
                    .andExpect(header("Authorization", "Bearer page-token"))
                    .andExpect(request -> {
                        String query = URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8);
                        assertTrue(query.contains("platform=messenger"), query);
                        assertTrue(query.contains(
                                "fields=participants,messages.limit(50){id,message,from,created_time,"
                                + "attachments{mime_type,name,file_url,image_data{url},video_data{url}}}"), query);
                        assertTrue(query.contains("limit=25"), query);
                        assertFalse(request.getURI().getRawQuery().contains("access_token"));
                    })
                    .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

            assertTrue(drain(client.conversations("page-1", "page-token")).isEmpty());
            server.verify();
        }
    }

    @Nested
    class Parsing {

        @BeforeEach
        void respond() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                    .andRespond(withSuccess(ONE_CONVERSATION, MediaType.APPLICATION_JSON));
        }

        @Test
        void messagesComeOldestFirstWithGraphTimes() {
            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals(List.of("m1", "m2", "m3"),
                    conversation.messages().stream().map(NormalizedMessage::externalMessageId).toList());
            assertEquals(Instant.parse("2026-11-20T10:00:00Z"), conversation.messages().get(0).sentAt());
            assertEquals(WebhookObject.PAGE, conversation.messages().get(0).channel());
        }

        @Test
        void theSenderBeingThePageMakesItOutboundAndOthersInbound() {
            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals(Direction.INBOUND, conversation.messages().get(0).direction());
            assertEquals(Direction.OUTBOUND, conversation.messages().get(2).direction());
            assertEquals("cust-1", conversation.peerId());
            assertEquals("cust-1", conversation.messages().get(2).peerId());
        }

        @Test
        void theCustomerNameComesFromTheParticipants() {
            assertEquals("Rina Akter", drain(client.conversations("page-1", "t")).get(0).peerName());
        }

        @Test
        void aMessageWithoutTextGetsTheAttachmentLabel() {
            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals("[Attachment]", conversation.messages().get(1).text());
        }

        @Test
        void aShortPageOfMessagesIsNotMarkedAsHavingMore() {
            assertFalse(drain(client.conversations("page-1", "t")).get(0).moreMessages());
        }
    }

    @Nested
    class Attachments {

        private NormalizedMessage onlyMessage(String attachmentsJson, String text) {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE)))
                    .andRespond(withSuccess("""
                            {"data":[{"id":"t_1","participants":{"data":[{"id":"page-1"},{"id":"cust-1"}]},
                             "messages":{"data":[{"id":"m1","message":"%s","from":{"id":"cust-1"},
                               "created_time":"2026-11-20T10:00:00+0000","attachments":{"data":[%s]}}]}}]}
                            """.formatted(text, attachmentsJson), MediaType.APPLICATION_JSON));
            return drain(client.conversations("page-1", "t")).get(0).messages().get(0);
        }

        @Test
        void imageDataBecomesAnImageAndLabelsAnEmptyText() {
            NormalizedMessage message = onlyMessage(
                    "{\"mime_type\":\"image/jpeg\",\"name\":\"p.jpg\",\"image_data\":{\"url\":\"https://cdn.example/p.jpg\"}}",
                    "");

            assertEquals("[Photo]", message.text());
            NormalizedAttachment image = message.attachments().get(0);
            assertEquals(AttachmentKind.IMAGE, image.kind());
            assertEquals("https://cdn.example/p.jpg", image.url());
            assertEquals("p.jpg", image.fileName());
            assertEquals("image/jpeg", image.contentType());
        }

        @Test
        void videoDataBecomesAVideoAndTextIsKept() {
            NormalizedMessage message = onlyMessage(
                    "{\"video_data\":{\"url\":\"https://cdn.example/v.mp4\"}}", "Watch");

            assertEquals("Watch", message.text());
            assertEquals(AttachmentKind.VIDEO, message.attachments().get(0).kind());
        }

        @Test
        void aFileUrlTakesItsKindFromTheMimeType() {
            NormalizedMessage message = onlyMessage(
                    "{\"mime_type\":\"audio/mpeg\",\"file_url\":\"https://cdn.example/a.mp3\"},"
                    + "{\"mime_type\":\"application/pdf\",\"name\":\"i.pdf\",\"file_url\":\"https://cdn.example/i.pdf\"}",
                    "");

            assertEquals("[Voice message]", message.text());
            assertEquals(AttachmentKind.AUDIO, message.attachments().get(0).kind());
            assertEquals(AttachmentKind.FILE, message.attachments().get(1).kind());
            assertEquals("i.pdf", message.attachments().get(1).fileName());
        }

        @Test
        void anAttachmentWithoutAWebUrlIsSkippedAndTheLabelStays() {
            NormalizedMessage message = onlyMessage(
                    "{\"id\":\"a1\"},{\"file_url\":\"javascript:alert(1)\"}", "");

            assertEquals("[Attachment]", message.text());
            assertTrue(message.attachments().isEmpty());
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void aConversationWithoutACustomerParticipantIsSkipped() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withSuccess("""
                    {"data":[{"id":"t","participants":{"data":[{"id":"page-1"}]},"messages":{"data":[]}}]}
                    """, MediaType.APPLICATION_JSON));

            assertTrue(drain(client.conversations("page-1", "t")).isEmpty());
        }

        @Test
        void theNameFallsBackToTheInboundSenderThenNull() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withSuccess("""
                    {"data":[{"id":"t","participants":{"data":[{"id":"page-1"},{"id":"cust-1"}]},
                     "messages":{"data":[{"id":"m1","message":"Hi","from":{"id":"cust-1","name":"Karim"},
                       "created_time":"2026-11-20T10:00:00+0000"}]}},
                     {"id":"t2","participants":{"data":[{"id":"page-1"},{"id":"cust-2"}]},"messages":{"data":[]}}]}
                    """, MediaType.APPLICATION_JSON));

            List<HistoryConversation> all = drain(client.conversations("page-1", "t"));

            assertEquals("Karim", all.get(0).peerName());
            assertNull(all.get(1).peerName());
        }

        @Test
        void messagesWithoutAnIdOrAReadableTimeAreDropped() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withSuccess("""
                    {"data":[{"id":"t","participants":{"data":[{"id":"page-1"},{"id":"cust-1"}]},
                     "messages":{"data":[
                       {"message":"no id","from":{"id":"cust-1"},"created_time":"2026-11-20T10:00:00+0000"},
                       {"id":"bad","message":"no time","from":{"id":"cust-1"},"created_time":"yesterday"},
                       {"id":"ok","message":"fine","from":{"id":"cust-1"},"created_time":"2026-11-20T10:00:00+0000"}]}}]}
                    """, MediaType.APPLICATION_JSON));

            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals(1, conversation.messages().size());
            assertEquals("ok", conversation.messages().get(0).externalMessageId());
        }

    }

    @Nested
    class MessagePaging {

        private static final String THREAD_URL = "https://graph.facebook.com/v27.0/t/messages";

        private static String message(int n) {
            return """
                    {"id":"m%d","message":"hi","from":{"id":"cust-1"},"created_time":"2026-11-20T10:%02d:00+0000"}"""
                    .formatted(n, n);
        }

        private static String edge(String next, int... ids) {
            List<String> data = new ArrayList<>();
            for (int id : ids) {
                data.add(message(id));
            }
            String paging = next == null ? "" : ",\"paging\":{\"next\":\"" + next + "\"}";
            return "{\"data\":[" + String.join(",", data) + "]" + paging + "}";
        }

        private void firstPage(String messagesEdge) {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withSuccess(
                    "{\"data\":[{\"id\":\"t\",\"participants\":{\"data\":[{\"id\":\"page-1\"},{\"id\":\"cust-1\"}]},"
                            + "\"messages\":" + messagesEdge + "}]}", MediaType.APPLICATION_JSON));
        }

        private void withLimits(int perConversation, int total) {
            withLimits(perConversation, total, 200);
        }

        private void withLimits(int perConversation, int total, int graphPages) {
            RestClient.Builder builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            client = new MessengerHistoryClient(META,
                    new HistoryImportProperties(true, 50, perConversation, total, 25, null, 1, 1, graphPages),
                    builder);
        }

        @Test
        void aMessagesLinkThatRepeatsWithEmptyDataEndsAndMarksMoreMessages() {
            firstPage(edge(THREAD_URL + "?after=a", 9));
            server.expect(requestTo(THREAD_URL + "?after=a"))
                    .andRespond(withSuccess(edge(THREAD_URL + "?after=a"), MediaType.APPLICATION_JSON));

            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals(1, conversation.messages().size());
            assertTrue(conversation.moreMessages());
            server.verify();
        }

        @Test
        void aGraphPageCapEndsAnEndlessChainOfDistinctMessageLinks() {
            withLimits(50, 1000, 3);
            firstPage(edge(THREAD_URL + "?after=0", 9));
            for (int i = 0; i < 2; i++) {
                server.expect(requestTo(THREAD_URL + "?after=" + i)).andRespond(withSuccess(
                        edge(THREAD_URL + "?after=" + (i + 1)), MediaType.APPLICATION_JSON));
            }

            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertTrue(conversation.moreMessages());
            server.verify();
        }

        @Test
        void followsAThreePageMessagesEdgeAndStoresOldestFirst() {
            firstPage(edge(THREAD_URL + "?after=a", 9, 8));
            server.expect(requestTo(THREAD_URL + "?after=a")).andExpect(header("Authorization", "Bearer t"))
                    .andRespond(withSuccess(edge(THREAD_URL + "?after=b", 7, 6), MediaType.APPLICATION_JSON));
            server.expect(requestTo(THREAD_URL + "?after=b"))
                    .andRespond(withSuccess(edge(null, 5), MediaType.APPLICATION_JSON));

            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals(List.of("m5", "m6", "m7", "m8", "m9"),
                    conversation.messages().stream().map(NormalizedMessage::externalMessageId).toList());
            assertFalse(conversation.moreMessages());
            server.verify();
        }

        @Test
        void stopsAtThePerConversationLimitAndMarksMoreMessages() {
            withLimits(3, 1000);
            firstPage(edge(THREAD_URL + "?after=a", 9, 8));
            server.expect(requestTo(THREAD_URL + "?after=a"))
                    .andRespond(withSuccess(edge(THREAD_URL + "?after=b", 7, 6), MediaType.APPLICATION_JSON));

            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals(List.of("m7", "m8", "m9"),
                    conversation.messages().stream().map(NormalizedMessage::externalMessageId).toList());
            assertTrue(conversation.moreMessages());
            server.verify();
        }

        @Test
        void stopsFollowingOnceTheTotalMessageCapIsReached() {
            withLimits(50, 2);
            firstPage(edge(THREAD_URL + "?after=a", 9, 8));

            HistoryConversation conversation = drain(client.conversations("page-1", "t")).get(0);

            assertEquals(2, conversation.messages().size());
            assertTrue(conversation.moreMessages());
            server.verify();
        }

        @Test
        void aNextLinkTokenIsNotSentAgain() {
            firstPage(edge(THREAD_URL + "?after=a&access_token=leak", 9));
            server.expect(requestTo(THREAD_URL + "?after=a")).andExpect(header("Authorization", "Bearer t"))
                    .andRespond(withSuccess(edge(null, 8), MediaType.APPLICATION_JSON));

            assertEquals(2, drain(client.conversations("page-1", "t")).get(0).messages().size());
            server.verify();
        }

        @Test
        void aMessagesNextLinkToAnotherHostIsRefused() {
            firstPage(edge("https://evil.example/m?after=a", 9));

            Iterator<HistoryConversation> it = client.conversations("page-1", "secret-token");

            assertThrows(HistoryFetchException.class, it::hasNext);
        }
    }

    @Nested
    class Paging {

        @Test
        void followsTheNextLinkOnlyAsTheIteratorIsConsumed() {
            String next = "https://graph.facebook.com/v27.0/page-1/conversations?after=abc";
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?platform")))
                    .andRespond(withSuccess(ONE_CONVERSATION.replace("]}\n", "],\"paging\":{\"next\":\"" + next
                            + "\"}}\n"), MediaType.APPLICATION_JSON));
            server.expect(requestTo(next)).andExpect(header("Authorization", "Bearer t"))
                    .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

            Iterator<HistoryConversation> it = client.conversations("page-1", "t");
            assertTrue(it.hasNext());
            it.next();
            assertFalse(it.hasNext());
            server.verify();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "https://evil.example.com/steal",
                "https://graph.facebook.com.evil.example/v27.0/x",
                "https://graph.facebook.com@evil.example/",
                "https://graph.facebook.com:8443/v27.0/x",
                "http://graph.facebook.com/v27.0/x",
                "not a uri",
                "//graph.facebook.com/v27.0/x"})
        void aNextLinkThatIsNotHttpsOnTheGraphHostIsRefused(String link) {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withSuccess(
                    "{\"data\":[],\"paging\":{\"next\":\"" + link + "\"}}", MediaType.APPLICATION_JSON));

            Iterator<HistoryConversation> it = client.conversations("page-1", "secret-token");

            HistoryFetchException e = assertThrows(HistoryFetchException.class, it::hasNext);
            assertFalse(String.valueOf(e.getMessage()).contains("secret-token"));
        }

        @Test
        void aConversationsLinkThatRepeatsWithEmptyDataTerminates() {
            String next = "https://graph.facebook.com/v27.0/page-1/conversations?after=abc";
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?platform")))
                    .andRespond(withSuccess(ONE_CONVERSATION.replace("]}\n", "],\"paging\":{\"next\":\"" + next
                            + "\"}}\n"), MediaType.APPLICATION_JSON));
            server.expect(requestTo(next)).andRespond(withSuccess(
                    "{\"data\":[],\"paging\":{\"next\":\"" + next + "&access_token=x\"}}",
                    MediaType.APPLICATION_JSON));

            List<HistoryConversation> all = drain(client.conversations("page-1", "t"));

            assertEquals(1, all.size());
            server.verify();
        }

        @Test
        void aGraphHostLinkInUpperCaseOrWithTheDefaultPortIsAccepted() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withSuccess(
                    "{\"data\":[],\"paging\":{\"next\":\"HTTPS://GRAPH.FACEBOOK.COM:443/v27.0/x?after=1\"}}",
                    MediaType.APPLICATION_JSON));
            server.expect(request -> assertEquals("after=1", request.getURI().getRawQuery()))
                    .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

            assertTrue(drain(client.conversations("page-1", "t")).isEmpty());
            server.verify();
        }

        @Test
        void aNextLinkToAnotherHostIsRefused() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withSuccess(
                    "{\"data\":[],\"paging\":{\"next\":\"https://evil.example.com/steal\"}}",
                    MediaType.APPLICATION_JSON));

            Iterator<HistoryConversation> it = client.conversations("page-1", "secret-token");

            HistoryFetchException e = assertThrows(HistoryFetchException.class, it::hasNext);
            assertFalse(String.valueOf(e.getMessage()).contains("secret-token"));
        }
    }

    @Nested
    class Failures {

        @Test
        void aPermissionErrorCodeIsAPermissionException() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withBadRequest()
                    .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":200,\"message\":\"x\"}}"));

            assertThrows(HistoryPermissionException.class, () -> client.conversations("page-1", "t").hasNext());
        }

        @Test
        void anExpiredTokenCodeIsAPermissionException() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withBadRequest()
                    .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":190}}"));

            assertThrows(HistoryPermissionException.class, () -> client.conversations("page-1", "t").hasNext());
        }

        @Test
        void anyOtherRefusalIsAFetchExceptionWithoutTheToken() {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE))).andRespond(withServerError());

            HistoryFetchException e = assertThrows(HistoryFetchException.class,
                    () -> client.conversations("page-1", "secret-token").hasNext());
            assertFalse(String.valueOf(e.getMessage()).contains("secret-token"));
        }

        @Test
        void reportsTheMessengerChannel() {
            assertEquals(Channel.MESSENGER, client.channel());
        }
    }
}
