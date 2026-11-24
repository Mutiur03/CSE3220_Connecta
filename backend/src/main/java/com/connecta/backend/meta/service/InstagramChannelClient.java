package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adapter: sends Instagram messages with the organization's account token through the Instagram API. */
@Component
class InstagramChannelClient extends GraphMessagingClient {

    private static final String GRAPH_HOST = "https://graph.instagram.com/";

    private final MetaConnectedAccountRepository accounts;
    private final TokenCipher cipher;
    private final RestClient graph;

    @Autowired
    InstagramChannelClient(MetaConnectedAccountRepository accounts, TokenCipher cipher, MetaProperties properties) {
        this(accounts, cipher, properties, RestClient.builder());
    }

    /** The builder is a parameter so tests can bind a mock server to it. */
    InstagramChannelClient(MetaConnectedAccountRepository accounts, TokenCipher cipher, MetaProperties properties,
            RestClient.Builder restClients) {
        this.accounts = accounts;
        this.cipher = cipher;
        this.graph = restClients.baseUrl(GRAPH_HOST + properties.graphVersion()).build();
    }

    @Override
    public Channel channel() {
        return Channel.INSTAGRAM;
    }

    @Override
    public String send(UUID organizationId, String recipientId, String text) {
        // The organization's first account sends until a conversation records its account (#243).
        MetaConnectedAccount account = accounts.findByOrganizationIdAndPlatform(organizationId,
                MetaConnectedAccount.PLATFORM_INSTAGRAM, PageRequest.of(0, 1)).stream()
                .findFirst().orElseThrow(ChannelNotConnectedException::new);
        String token = cipher.decrypt(account.getAccessTokenEncrypted());
        return post(graph.post().uri("/me/messages").header(HttpHeaders.AUTHORIZATION, "Bearer " + token),
                payload(recipientId, text));
    }
}
