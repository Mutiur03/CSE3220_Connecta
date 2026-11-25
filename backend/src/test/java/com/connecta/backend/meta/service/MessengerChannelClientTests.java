package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.support.TestUsers;
import java.util.Optional;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.org.domain.Organization;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MessengerChannelClientTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String URL = "https://graph.facebook.com/v27.0/me/messages";

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private Conversation thread;
    private MetaConnectedPageRepository pages;
    private TokenCipher cipher;
    private MockRestServiceServer server;
    private MessengerChannelClient client;

    @BeforeEach
    void setUp() {
        pages = mock(MetaConnectedPageRepository.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MetaProperties properties = new MetaProperties("app", "secret", "v27.0", "config", "https://x", KEY);
        cipher = new TokenCipher(properties);
        TestUsers.set(organization, "id", organizationId);
        MetaConnectedPage page = new MetaConnectedPage(organization, "page-1");
        page.replace("Page", cipher.encrypt("page-token"), null, Instant.EPOCH);
        when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(page)));
        client = new MessengerChannelClient(pages, cipher, properties, builder);
        thread = new Conversation(organization, Channel.MESSENGER, "psid-1");
    }

    @Test
    void postsTheTextToTheSendApiWithThePageTokenAndReturnsTheMessageId() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer page-token"))
                .andExpect(jsonPath("$.recipient.id").value("psid-1"))
                .andExpect(jsonPath("$.message.text").value("hello"))
                .andRespond(withSuccess("{\"recipient_id\":\"psid-1\",\"message_id\":\"mid.1\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("mid.1", client.send(thread, "hello"));
        server.verify();
    }

    @Test
    void theSendRequestCarriesNoAccessTokenInTheUrl() {
        server.expect(request -> assertNull(request.getURI().getRawQuery()))
                .andExpect(header("Authorization", "Bearer page-token"))
                .andRespond(withSuccess("{\"message_id\":\"mid.1\"}", MediaType.APPLICATION_JSON));

        client.send(thread, "hello");

        server.verify();
    }

    @Test
    void noConnectedPageIsNotConnected() {
        when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThrows(ChannelNotConnectedException.class, () -> client.send(thread, "hello"));
    }

    @Test
    void anOutsideTheWindowErrorIsAClosedWindow() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":10,\"error_subcode\":2018278}}"));

        assertThrows(MessagingWindowClosedException.class, () -> client.send(thread, "hello"));
    }

    @Test
    void anyOtherGraphErrorIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":100}}"));

        assertThrows(ChannelSendFailedException.class, () -> client.send(thread, "hello"));
    }

    @Test
    void aServerErrorWithoutABodyIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThrows(ChannelSendFailedException.class, () -> client.send(thread, "hello"));
    }

    @Test
    void aResponseWithoutAMessageIdIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(ChannelSendFailedException.class, () -> client.send(thread, "hello"));
    }

    @Nested
    class ConversationAccount {

        private MetaConnectedPage pageWithToken(String pageId, String token) {
            MetaConnectedPage page = new MetaConnectedPage(organization, pageId);
            page.replace("Page " + pageId, cipher.encrypt(token), null, Instant.EPOCH);
            TestUsers.set(page, "id", UUID.randomUUID());
            return page;
        }

        private void respondOkTo(String token) {
            server.expect(requestTo(URL))
                    .andExpect(header("Authorization", "Bearer " + token))
                    .andRespond(withSuccess("{\"message_id\":\"mid.2\"}", MediaType.APPLICATION_JSON));
        }

        @Test
        void sendsWithTheTokenOfThePageTheConversationBelongsToEvenWhenTheOrganizationHasTwo() {
            MetaConnectedPage second = pageWithToken("page-2", "second-token");
            thread.linkToPage(second);
            when(pages.findByIdAndOrganizationId(second.getId(), organizationId)).thenReturn(Optional.of(second));
            respondOkTo("second-token");

            assertEquals("mid.2", client.send(thread, "hello"));
            server.verify();
        }

        @Test
        void aLinkedPageThatIsGoneIsNotConnected() {
            MetaConnectedPage removed = pageWithToken("page-3", "gone-token");
            thread.linkToPage(removed);
            when(pages.findByIdAndOrganizationId(removed.getId(), organizationId)).thenReturn(Optional.empty());

            assertThrows(ChannelNotConnectedException.class, () -> client.send(thread, "hello"));
        }

        @Test
        void anUnlinkedConversationFallsBackToTheOnlyPage() {
            respondOkTo("page-token");

            assertEquals("mid.2", client.send(thread, "hello"));
        }

        @Test
        void anUnlinkedConversationOfAnOrganizationWithSeveralPagesIsRefused() {
            when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(pageWithToken("a", "t1"), pageWithToken("b", "t2"))));

            assertThrows(ConversationNotLinkedException.class, () -> client.send(thread, "hello"));
        }
    }

    @Nested
    class Attachments {

        private static final String FILE_URL = "https://api.example.com/api/files/1";

        @Test
        void supportsEveryKind() {
            for (AttachmentKind kind : AttachmentKind.values()) {
                assertTrue(client.supports(kind), kind.name());
            }
        }

        @Test
        void postsTheAttachmentPayloadWithTheReusableUrlAndReturnsTheMessageId() {
            server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer page-token"))
                    .andExpect(jsonPath("$.recipient.id").value("psid-1"))
                    .andExpect(jsonPath("$.message.attachment.type").value("image"))
                    .andExpect(jsonPath("$.message.attachment.payload.url").value(FILE_URL))
                    .andExpect(jsonPath("$.message.attachment.payload.is_reusable").value(true))
                    .andExpect(jsonPath("$.message.text").doesNotExist())
                    .andRespond(withSuccess("{\"message_id\":\"mid.9\"}", MediaType.APPLICATION_JSON));

            assertEquals("mid.9", client.sendAttachment(thread, AttachmentKind.IMAGE, FILE_URL));
            server.verify();
        }

        @Test
        void mapsEachKindToTheGraphType() {
            server.expect(jsonPath("$.message.attachment.type").value("video"))
                    .andRespond(withSuccess("{\"message_id\":\"m1\"}", MediaType.APPLICATION_JSON));
            server.expect(jsonPath("$.message.attachment.type").value("audio"))
                    .andRespond(withSuccess("{\"message_id\":\"m2\"}", MediaType.APPLICATION_JSON));
            server.expect(jsonPath("$.message.attachment.type").value("file"))
                    .andRespond(withSuccess("{\"message_id\":\"m3\"}", MediaType.APPLICATION_JSON));

            client.sendAttachment(thread, AttachmentKind.VIDEO, FILE_URL);
            client.sendAttachment(thread, AttachmentKind.AUDIO, FILE_URL);
            client.sendAttachment(thread, AttachmentKind.FILE, FILE_URL);

            server.verify();
        }

        @Test
        void aClosedWindowAndOtherErrorsMapLikeText() {
            server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"code\":10}}"));
            server.expect(requestTo(URL)).andRespond(withServerError());

            assertThrows(MessagingWindowClosedException.class,
                    () -> client.sendAttachment(thread, AttachmentKind.IMAGE, FILE_URL));
            assertThrows(ChannelSendFailedException.class,
                    () -> client.sendAttachment(thread, AttachmentKind.IMAGE, FILE_URL));
        }
    }
}
