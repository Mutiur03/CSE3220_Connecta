package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adapter: sends Messenger messages and attachments (every kind) with the Page token through the Graph Send API. */
@Component
class MessengerChannelClient extends GraphMessagingClient {

    private static final String GRAPH_HOST = "https://graph.facebook.com/";

    private final MetaConnectedPageRepository pages;
    private final TokenCipher cipher;
    private final RestClient graph;

    @Autowired
    MessengerChannelClient(MetaConnectedPageRepository pages, TokenCipher cipher, MetaProperties properties) {
        this(pages, cipher, properties, RestClient.builder());
    }

    /** The builder is a parameter so tests can bind a mock server to it. */
    MessengerChannelClient(MetaConnectedPageRepository pages, TokenCipher cipher, MetaProperties properties,
            RestClient.Builder restClients) {
        this.pages = pages;
        this.cipher = cipher;
        this.graph = restClients.baseUrl(GRAPH_HOST + properties.graphVersion()).build();
    }

    @Override
    public Channel channel() {
        return Channel.MESSENGER;
    }

    @Override
    public boolean supports(AttachmentKind kind) {
        return true;
    }

    @Override
    protected RestClient.RequestBodySpec messagesRequest(Conversation conversation) {
        String token = cipher.decrypt(pageOf(conversation).getPageAccessTokenEncrypted());
        return graph.post().uri("/me/messages").headers(GraphAuth.bearer(token));
    }

    private MetaConnectedPage pageOf(Conversation conversation) {
        UUID organizationId = conversation.getOrganization().getId();
        if (conversation.getMetaPage() != null) {
            return pages.findByIdAndOrganizationId(conversation.getMetaPage().getId(), organizationId)
                    .orElseThrow(ChannelNotConnectedException::new);
        }
        return onlyAccount(pages.findByOrganizationId(organizationId, PageRequest.of(0, 2)).getContent());
    }
}
