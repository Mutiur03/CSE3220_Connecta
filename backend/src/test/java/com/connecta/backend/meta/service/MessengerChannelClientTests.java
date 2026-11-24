package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
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

class MessengerChannelClientTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";
    private static final String URL = "https://graph.facebook.com/v27.0/me/messages?access_token=page-token";

    private final UUID organizationId = UUID.randomUUID();
    private MetaConnectedPageRepository pages;
    private MockRestServiceServer server;
    private MessengerChannelClient client;

    @BeforeEach
    void setUp() {
        pages = mock(MetaConnectedPageRepository.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        MetaProperties properties = new MetaProperties("app", "secret", "v27.0", "config", "https://x", KEY);
        TokenCipher cipher = new TokenCipher(properties);
        MetaConnectedPage page = new MetaConnectedPage(new Organization("Acme", "acme"), "page-1");
        page.replace("Page", cipher.encrypt("page-token"), null, Instant.EPOCH);
        when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(page)));
        client = new MessengerChannelClient(pages, cipher, properties, builder);
    }

    @Test
    void postsTheTextToTheSendApiWithThePageTokenAndReturnsTheMessageId() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.recipient.id").value("psid-1"))
                .andExpect(jsonPath("$.message.text").value("hello"))
                .andRespond(withSuccess("{\"recipient_id\":\"psid-1\",\"message_id\":\"mid.1\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals("mid.1", client.send(organizationId, "psid-1", "hello"));
        server.verify();
    }

    @Test
    void noConnectedPageIsNotConnected() {
        when(pages.findByOrganizationId(any(UUID.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThrows(ChannelNotConnectedException.class, () -> client.send(organizationId, "psid-1", "hello"));
    }

    @Test
    void anOutsideTheWindowErrorIsAClosedWindow() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":10,\"error_subcode\":2018278}}"));

        assertThrows(MessagingWindowClosedException.class, () -> client.send(organizationId, "psid-1", "hello"));
    }

    @Test
    void anyOtherGraphErrorIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"x\",\"code\":100}}"));

        assertThrows(ChannelSendFailedException.class, () -> client.send(organizationId, "psid-1", "hello"));
    }

    @Test
    void aServerErrorWithoutABodyIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThrows(ChannelSendFailedException.class, () -> client.send(organizationId, "psid-1", "hello"));
    }

    @Test
    void aResponseWithoutAMessageIdIsASendFailure() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThrows(ChannelSendFailedException.class, () -> client.send(organizationId, "psid-1", "hello"));
    }
}
