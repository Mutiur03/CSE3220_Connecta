package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;

class InstagramHistoryClientTests {

    private static final MetaProperties META = new MetaProperties("app", "secret", "v27.0", "config",
            "https://x", "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=");

    @Test
    void readsTheInstagramHostWithTheAccountTokenAndUsesTheUsernameAsName() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InstagramHistoryClient client = new InstagramHistoryClient(META, HistoryImportProperties.defaults(), builder);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.instagram.com/v27.0/ig-1/conversations")))
                .andExpect(request -> org.junit.jupiter.api.Assertions.assertTrue(
                        java.net.URLDecoder.decode(request.getURI().getRawQuery(),
                                java.nio.charset.StandardCharsets.UTF_8).contains("platform=instagram")))
                .andExpect(header("Authorization", "Bearer ig-token"))
                .andRespond(withSuccess("""
                        {"data":[{"id":"t","participants":{"data":[{"id":"ig-1","username":"shop"},
                           {"id":"igsid-9","username":"rina_k"}]},
                         "messages":{"data":[
                           {"id":"m2","message":"Sure","from":{"id":"ig-1","username":"shop"},"created_time":"2026-11-20T10:01:00+0000"},
                           {"id":"m1","message":"Price?","from":{"id":"igsid-9","username":"rina_k"},"created_time":"2026-11-20T10:00:00+0000",
                            "attachments":{"data":[{"image_data":{"url":"https://lookaside.example/ig.jpg"}}]}}]}}]}
                        """, MediaType.APPLICATION_JSON));

        List<HistoryConversation> all = new ArrayList<>();
        client.conversations("ig-1", "ig-token").forEachRemaining(all::add);

        HistoryConversation conversation = all.get(0);
        assertEquals("rina_k", conversation.peerName());
        assertEquals("igsid-9", conversation.peerId());
        assertEquals(WebhookObject.INSTAGRAM, conversation.messages().get(0).channel());
        assertEquals(Direction.INBOUND, conversation.messages().get(0).direction());
        assertEquals(Direction.OUTBOUND, conversation.messages().get(1).direction());
        assertEquals("https://lookaside.example/ig.jpg", conversation.messages().get(0).attachments().get(0).url());
        assertEquals(Channel.INSTAGRAM, client.channel());
        server.verify();
    }

    @Test
    void theFirstRequestKeepsASmallMessagePageEvenWhenALargeCapIsConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        InstagramHistoryClient client = new InstagramHistoryClient(META,
                new HistoryImportProperties(true, 0, 500, 0, 5, null, 1, 1, 1), builder);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://graph.instagram.com/v27.0/ig-1/conversations")))
                .andExpect(request -> {
                    String query = java.net.URLDecoder.decode(request.getURI().getRawQuery(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    assertTrue(query.contains("messages.limit(50)"), query);
                })
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        client.conversations("ig-1", "ig-token").forEachRemaining(conversation -> { });

        server.verify();
    }

    @Nested
    class SharedPosts {

        private static final String GRAPH = "https://graph.instagram.com/v27.0/";
        private static final String TWO_LABEL_ONLY = """
                {"data":[{"id":"t","participants":{"data":[{"id":"ig-1","username":"shop"},
                   {"id":"igsid-9","username":"rina_k"}]},
                 "messages":{"data":[
                   {"id":"m2","message":"","from":{"id":"igsid-9"},"created_time":"2026-11-20T10:01:00+0000"},
                   {"id":"m1","from":{"id":"igsid-9"},"created_time":"2026-11-20T10:00:00+0000"}]}}]}
                """;
        private static final String SHARES = """
                {"shares":{"data":[{"link":"https://www.instagram.com/p/abc/"}],"paging":{}},"id":"m"}
                """;

        private MockRestServiceServer server;
        private InstagramHistoryClient client;

        private void connect(HistoryImportProperties limits) {
            RestClient.Builder builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            client = new InstagramHistoryClient(META, limits, builder);
        }

        private void conversations(String body) {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "ig-1/conversations")))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }

        private void shares(String messageId, ResponseCreator answer) {
            server.expect(requestTo(GRAPH + messageId + "?fields=shares"))
                    .andExpect(header("Authorization", "Bearer ig-token"))
                    .andRespond(answer);
        }

        private List<NormalizedMessage> messages() {
            List<HistoryConversation> all = new ArrayList<>();
            client.conversations("ig-1", "ig-token").forEachRemaining(all::add);
            return all.get(0).messages();
        }

        @Test
        void aLabelOnlyMessageIsLookedUpAndItsSharedPostBecomesALink() {
            connect(HistoryImportProperties.defaults());
            conversations(TWO_LABEL_ONLY);
            shares("m2", withSuccess(SHARES, MediaType.APPLICATION_JSON));
            shares("m1", withSuccess("{\"id\":\"m1\"}", MediaType.APPLICATION_JSON));

            List<NormalizedMessage> messages = messages();

            NormalizedMessage shared = messages.get(1);
            assertEquals("[Shared post]", shared.text());
            assertEquals(1, shared.attachments().size());
            assertEquals(AttachmentKind.LINK, shared.attachments().get(0).kind());
            assertEquals("https://www.instagram.com/p/abc/", shared.attachments().get(0).url());
            assertEquals("Shared post", shared.attachments().get(0).fileName());
            assertEquals("[Attachment]", messages.get(0).text());
            assertTrue(messages.get(0).attachments().isEmpty());
            server.verify();
        }

        @Test
        void aFailedOrTimedOutLookupLeavesThatMessageLabelOnlyAndTheImportGoesOn() {
            connect(HistoryImportProperties.defaults());
            conversations(TWO_LABEL_ONLY);
            shares("m2", withException(new SocketTimeoutException("read timed out")));
            shares("m1", withServerError());

            List<NormalizedMessage> messages = messages();

            assertEquals(2, messages.size());
            messages.forEach(message -> {
                assertEquals("[Attachment]", message.text());
                assertTrue(message.attachments().isEmpty());
            });
            server.verify();
        }

        @Test
        void aShareLinkThatIsNotAWebUrlIsDropped() {
            connect(HistoryImportProperties.defaults());
            conversations(TWO_LABEL_ONLY);
            shares("m2", withSuccess("{\"shares\":{\"data\":[{\"link\":\"javascript:alert(1)\"}]}}",
                    MediaType.APPLICATION_JSON));
            shares("m1", withSuccess("{}", MediaType.APPLICATION_JSON));

            assertTrue(messages().get(1).attachments().isEmpty());
            server.verify();
        }

        @Test
        void aMessageWithTextOrRealAttachmentsIsNotLookedUp() {
            connect(HistoryImportProperties.defaults());
            conversations("""
                    {"data":[{"id":"t","participants":{"data":[{"id":"ig-1"},{"id":"igsid-9"}]},
                     "messages":{"data":[
                       {"id":"m2","message":"","from":{"id":"igsid-9"},"created_time":"2026-11-20T10:01:00+0000",
                        "attachments":{"data":[{"image_data":{"url":"https://lookaside.example/ig.jpg"}}]}},
                       {"id":"m1","message":"Hi","from":{"id":"igsid-9"},"created_time":"2026-11-20T10:00:00+0000"}]}}]}
                    """);

            List<NormalizedMessage> messages = messages();

            assertEquals("[Photo]", messages.get(1).text());
            server.verify();
        }

        @Test
        void lookupsStopWhenTheirOwnBudgetIsSpent() {
            connect(new HistoryImportProperties(true, 50, 50, 1000, 5, null, 1, 1, 1));
            conversations(TWO_LABEL_ONLY);
            shares("m2", withSuccess(SHARES, MediaType.APPLICATION_JSON));

            List<NormalizedMessage> messages = messages();

            assertEquals("[Shared post]", messages.get(1).text());
            assertEquals("[Attachment]", messages.get(0).text());
            server.verify();
        }

        @Test
        void lookupsDoNotUseUpThePagingBudgetSoTheNextPageOfConversationsIsStillRead() {
            connect(new HistoryImportProperties(true, 50, 50, 1000, 5, null, 1, 1, 2));
            conversations("""
                    {"data":[{"id":"t","participants":{"data":[{"id":"ig-1","username":"shop"},
                       {"id":"igsid-9","username":"rina_k"}]},
                     "messages":{"data":[
                       {"id":"m2","message":"","from":{"id":"igsid-9"},"created_time":"2026-11-20T10:01:00+0000"},
                       {"id":"m1","from":{"id":"igsid-9"},"created_time":"2026-11-20T10:00:00+0000"}]}}],
                     "paging":{"next":"https://graph.instagram.com/v27.0/ig-1/conversations?after=p2"}}
                    """);
            shares("m2", withSuccess(SHARES, MediaType.APPLICATION_JSON));
            shares("m1", withSuccess("{\"id\":\"m1\"}", MediaType.APPLICATION_JSON));
            conversations("""
                    {"data":[{"id":"t2","participants":{"data":[{"id":"ig-1","username":"shop"},
                       {"id":"igsid-10","username":"karim"}]},
                     "messages":{"data":[
                       {"id":"m3","message":"Hello","from":{"id":"igsid-10"},"created_time":"2026-11-20T11:00:00+0000"}]}}]}
                    """);

            List<HistoryConversation> all = new ArrayList<>();
            client.conversations("ig-1", "ig-token").forEachRemaining(all::add);

            assertEquals(2, all.size());
            assertEquals("igsid-10", all.get(1).peerId());
            server.verify();
        }
    }

    @Nested
    class Since {

        private static final String GRAPH = "https://graph.instagram.com/v27.0/";
        private static final String NEXT_PAGE = GRAPH + "ig-1/conversations?after=p2";
        private static final Instant SINCE = Instant.parse("2026-11-20T10:00:00Z");
        private static final String NEW = "2026-11-20T11:00:00+0000";
        private static final String NEWER = "2026-11-20T10:30:00+0000";
        private static final String OLD = "2026-11-20T09:00:00+0000";

        private MockRestServiceServer server;
        private InstagramHistoryClient client;

        private void connect() {
            RestClient.Builder builder = RestClient.builder();
            server = MockRestServiceServer.bindTo(builder).build();
            client = new InstagramHistoryClient(META, HistoryImportProperties.defaults(), builder);
        }

        private void page(String body) {
            server.expect(requestTo(org.hamcrest.Matchers.startsWith(GRAPH + "ig-1/conversations")))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        }

        private static String message(String text, String at) {
            return "{\"id\":\"" + text + "\",\"message\":\"" + text
                    + "\",\"from\":{\"id\":\"igsid-9\"},\"created_time\":\"" + at + "\"}";
        }

        private static String conversation(String id, String messagesPaging, String... messages) {
            return "{\"id\":\"" + id + "\",\"participants\":{\"data\":[{\"id\":\"ig-1\",\"username\":\"shop\"},"
                    + "{\"id\":\"igsid-" + id + "\",\"username\":\"user-" + id + "\"}]},"
                    + "\"messages\":{\"data\":[" + String.join(",", messages) + "]" + messagesPaging + "}}";
        }

        private static String list(String next, String... conversations) {
            String paging = next == null ? "" : ",\"paging\":{\"next\":\"" + next + "\"}";
            return "{\"data\":[" + String.join(",", conversations) + "]" + paging + "}";
        }

        private List<HistoryConversation> read() {
            List<HistoryConversation> all = new ArrayList<>();
            client.conversations("ig-1", "ig-token", new HistoryBounds(1000, 200, 200_000, SINCE))
                    .forEachRemaining(all::add);
            return all;
        }

        @Test
        void theReadStopsAtTheFirstConversationOlderThanSinceAndAsksForNoMorePages() {
            connect();
            page(list(NEXT_PAGE,
                    conversation("c1", "", message("c1-new", NEW)),
                    conversation("c2", "", message("c2-old", OLD)),
                    conversation("c3", "", message("c3-new", NEW))));

            List<HistoryConversation> all = read();

            assertEquals(1, all.size());
            assertEquals("igsid-c1", all.get(0).peerId());
            server.verify();
        }

        @Test
        void furtherPagesAreReadWhileEveryConversationIsNewerThanSince() {
            connect();
            page(list(NEXT_PAGE,
                    conversation("c1", "", message("c1-new", NEW)),
                    conversation("c2", "", message("c2-new", NEWER))));
            page(list(null,
                    conversation("c3", "", message("c3-new", NEW)),
                    conversation("c4", "", message("c4-old", OLD))));

            List<HistoryConversation> all = read();

            assertEquals(3, all.size());
            assertEquals("igsid-c3", all.get(2).peerId());
            server.verify();
        }

        @Test
        void messagesOlderThanSinceAreLeftOutOfAChangedConversation() {
            connect();
            page(list(null, conversation("c1", "",
                    message("m3", NEW), message("m2", NEWER), message("m1", OLD))));

            List<HistoryConversation> all = read();

            assertEquals(1, all.size());
            assertEquals(List.of("m2", "m3"), all.get(0).messages().stream().map(NormalizedMessage::text).toList());
            server.verify();
        }

        @Test
        void aConversationsMessagePagingStopsOnceAPageReachesTheMessagesFromBeforeSince() {
            connect();
            String messagesNext = ",\"paging\":{\"next\":\"" + GRAPH + "c1/messages?after=m\"}";
            page(list(null, conversation("c1", messagesNext, message("m2", NEW), message("m1", OLD))));

            List<HistoryConversation> all = read();

            assertEquals(List.of("m2"), all.get(0).messages().stream().map(NormalizedMessage::text).toList());
            assertEquals(false, all.get(0).moreMessages());
            server.verify();
        }
    }
}
