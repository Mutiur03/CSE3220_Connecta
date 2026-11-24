package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.org.domain.Organization;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
    private MetaConnectedAccountRepository accounts;
    private MockRestServiceServer server;
    private InstagramChannelClient client;

    @BeforeEach
    void setUp() {
        accounts = mock(MetaConnectedAccountRepository.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MetaProperties properties = new MetaProperties("app", "secret", "v27.0", "config", "https://x", KEY);
        TokenCipher cipher = new TokenCipher(properties);
        MetaConnectedAccount account = new MetaConnectedAccount(new Organization("Acme", "acme"),
                MetaConnectedAccount.PLATFORM_INSTAGRAM, "ig-1");
        account.refresh("shop", cipher.encrypt("ig-token"), null, Instant.EPOCH);
        when(accounts.findByOrganizationIdAndPlatform(any(UUID.class), eq("INSTAGRAM"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
        client = new InstagramChannelClient(accounts, cipher, properties, builder);
    }

    @Test
    void postsTheTextWithABearerTokenAndReturnsTheMessageId() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer ig-token"))
                .andExpect(jsonPath("$.recipient.id").value("igsid-1"))
                .andExpect(jsonPath("$.message.text").value("hello"))
                .andRespond(withSuccess("{\"recipient_id\":\"igsid-1\",\"message_id\":\"aWdfZA\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("aWdfZA", client.send(organizationId, "igsid-1", "hello"));
        server.verify();
    }

    @Test
    void noConnectedAccountIsNotConnected() {
        when(accounts.findByOrganizationIdAndPlatform(any(UUID.class), eq("INSTAGRAM"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThrows(ChannelNotConnectedException.class, () -> client.send(organizationId, "igsid-1", "hello"));
    }

    @Test
    void anOutsideTheWindowErrorIsAClosedWindow() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":10}}"));

        assertThrows(MessagingWindowClosedException.class, () -> client.send(organizationId, "igsid-1", "hello"));
    }

    @Test
    void anyOtherGraphErrorIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":190}}"));

        assertThrows(ChannelSendFailedException.class, () -> client.send(organizationId, "igsid-1", "hello"));
    }
}
