package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.support.TestUsers;
import java.util.Optional;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
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

class InstagramChannelClientTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String URL = "https://graph.instagram.com/v27.0/me/messages";

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private Conversation thread;
    private MetaConnectedAccountRepository accounts;
    private TokenCipher cipher;
    private MockRestServiceServer server;
    private InstagramChannelClient client;

    @BeforeEach
    void setUp() {
        accounts = mock(MetaConnectedAccountRepository.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MetaProperties properties = new MetaProperties("app", "secret", "v27.0", "config", "https://x", KEY);
        cipher = new TokenCipher(properties);
        TestUsers.set(organization, "id", organizationId);
        MetaConnectedAccount account = new MetaConnectedAccount(organization,
                MetaConnectedAccount.PLATFORM_INSTAGRAM, "ig-1");
        account.refresh("shop", cipher.encrypt("ig-token"), null, Instant.EPOCH);
        when(accounts.findByOrganizationIdAndPlatform(any(UUID.class), eq("INSTAGRAM"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
        client = new InstagramChannelClient(accounts, cipher, properties, builder);
        thread = new Conversation(organization, Channel.INSTAGRAM, "igsid-1");
    }

    @Test
    void postsTheTextWithABearerTokenAndReturnsTheMessageId() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ig-token"))
                .andExpect(jsonPath("$.recipient.id").value("igsid-1"))
                .andExpect(jsonPath("$.message.text").value("hello"))
                .andRespond(withSuccess("{\"recipient_id\":\"igsid-1\",\"message_id\":\"aWdfZA\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("aWdfZA", client.send(thread, "hello"));
        server.verify();
    }

    @Test
    void noConnectedAccountIsNotConnected() {
        when(accounts.findByOrganizationIdAndPlatform(any(UUID.class), eq("INSTAGRAM"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThrows(ChannelNotConnectedException.class, () -> client.send(thread, "hello"));
    }

    @Test
    void anOutsideTheWindowErrorIsAClosedWindow() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":10}}"));

        assertThrows(MessagingWindowClosedException.class, () -> client.send(thread, "hello"));
    }

    @Test
    void anyOtherGraphErrorIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":190}}"));

        assertThrows(ChannelSendFailedException.class, () -> client.send(thread, "hello"));
    }

    @Nested
    class ConversationAccount {

        private MetaConnectedAccount accountWithToken(String externalId, String token) {
            MetaConnectedAccount account = new MetaConnectedAccount(organization,
                    MetaConnectedAccount.PLATFORM_INSTAGRAM, externalId);
            account.refresh("shop", cipher.encrypt(token), null, Instant.EPOCH);
            TestUsers.set(account, "id", UUID.randomUUID());
            return account;
        }

        @Test
        void sendsWithTheTokenOfTheAccountTheConversationBelongsToEvenWhenTheOrganizationHasTwo() {
            MetaConnectedAccount second = accountWithToken("ig-2", "second-token");
            thread.linkToInstagramAccount(second);
            when(accounts.findByIdAndOrganizationId(second.getId(), organizationId))
                    .thenReturn(Optional.of(second));
            server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer second-token"))
                    .andRespond(withSuccess("{\"message_id\":\"mid.2\"}", MediaType.APPLICATION_JSON));

            assertEquals("mid.2", client.send(thread, "hello"));
            server.verify();
        }

        @Test
        void aLinkedAccountThatIsGoneIsNotConnected() {
            MetaConnectedAccount removed = accountWithToken("ig-3", "gone-token");
            thread.linkToInstagramAccount(removed);
            when(accounts.findByIdAndOrganizationId(removed.getId(), organizationId)).thenReturn(Optional.empty());

            assertThrows(ChannelNotConnectedException.class, () -> client.send(thread, "hello"));
        }

        @Test
        void anUnlinkedConversationFallsBackToTheOnlyAccount() {
            server.expect(requestTo(URL)).andExpect(header("Authorization", "Bearer ig-token"))
                    .andRespond(withSuccess("{\"message_id\":\"mid.2\"}", MediaType.APPLICATION_JSON));

            assertEquals("mid.2", client.send(thread, "hello"));
        }

        @Test
        void anUnlinkedConversationOfAnOrganizationWithSeveralAccountsIsRefused() {
            when(accounts.findByOrganizationIdAndPlatform(any(UUID.class), eq("INSTAGRAM"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(accountWithToken("a", "t1"), accountWithToken("b", "t2"))));

            assertThrows(ConversationNotLinkedException.class, () -> client.send(thread, "hello"));
        }
    }

    @Nested
    class Attachments {

        private static final String FILE_URL = "https://api.example.com/api/files/1";

        @Test
        void supportsImageVideoAndAudioButNotAGenericFile() {
            assertTrue(client.supports(AttachmentKind.IMAGE));
            assertTrue(client.supports(AttachmentKind.VIDEO));
            assertTrue(client.supports(AttachmentKind.AUDIO));
            assertFalse(client.supports(AttachmentKind.FILE));
        }

        @Test
        void postsTheAttachmentPayloadWithTheAccountToken() {
            server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer ig-token"))
                    .andExpect(jsonPath("$.recipient.id").value("igsid-1"))
                    .andExpect(jsonPath("$.message.attachment.type").value("audio"))
                    .andExpect(jsonPath("$.message.attachment.payload.url").value(FILE_URL))
                    .andExpect(jsonPath("$.message.attachment.payload.is_reusable").doesNotExist())
                    .andRespond(withSuccess("{\"message_id\":\"aWdfZB\"}", MediaType.APPLICATION_JSON));

            assertEquals("aWdfZB", client.sendAttachment(thread, AttachmentKind.AUDIO, FILE_URL));
            server.verify();
        }

        @Test
        void aGenericFileIsRefusedWithoutCallingInstagram() {
            assertThrows(UnsupportedAttachmentException.class,
                    () -> client.sendAttachment(thread, AttachmentKind.FILE, FILE_URL));

            server.verify();
        }
    }
}
