package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapter: sends Instagram messages and attachments with the account token through the Instagram API. Instagram
 * delivers images, video and audio, not generic files.
 */
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
    public boolean supports(AttachmentKind kind) {
        return kind != AttachmentKind.FILE;
    }

    @Override
    protected RestClient.RequestBodySpec messagesRequest(Conversation conversation) {
        String token = cipher.decrypt(accountOf(conversation).getAccessTokenEncrypted());
        return graph.post().uri("/me/messages").headers(GraphAuth.bearer(token));
    }

    private MetaConnectedAccount accountOf(Conversation conversation) {
        UUID organizationId = conversation.getOrganization().getId();
        if (conversation.getInstagramAccount() != null) {
            return accounts.findByIdAndOrganizationId(conversation.getInstagramAccount().getId(), organizationId)
                    .orElseThrow(ChannelNotConnectedException::new);
        }
        return onlyAccount(accounts.findByOrganizationIdAndPlatform(organizationId,
                MetaConnectedAccount.PLATFORM_INSTAGRAM, PageRequest.of(0, 2)).getContent());
    }
}
