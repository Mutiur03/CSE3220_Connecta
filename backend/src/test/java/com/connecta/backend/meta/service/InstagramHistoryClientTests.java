package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
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
                           {"id":"m1","message":"Price?","from":{"id":"igsid-9","username":"rina_k"},"created_time":"2026-11-20T10:00:00+0000"}]}}]}
                        """, MediaType.APPLICATION_JSON));

        List<HistoryConversation> all = new ArrayList<>();
        client.conversations("ig-1", "ig-token").forEachRemaining(all::add);

        HistoryConversation conversation = all.get(0);
        assertEquals("rina_k", conversation.peerName());
        assertEquals("igsid-9", conversation.peerId());
        assertEquals(WebhookObject.INSTAGRAM, conversation.messages().get(0).channel());
        assertEquals(Direction.INBOUND, conversation.messages().get(0).direction());
        assertEquals(Direction.OUTBOUND, conversation.messages().get(1).direction());
        assertEquals(Channel.INSTAGRAM, client.channel());
        server.verify();
    }
}
