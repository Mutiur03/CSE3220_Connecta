package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.config.ProfileLookupProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.NamelessCustomer;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Adapter: reads a Messenger user's name with the Page token (Graph {@code GET /{psid}?fields=name}). */
@Component
class MessengerCustomerProfileClient extends GraphCustomerProfileClient {

    private static final String GRAPH_HOST = "https://graph.facebook.com/";

    private final MetaConnectedPageRepository pages;
    private final TokenCipher cipher;
    private final RestClient graph;

    @Autowired
    MessengerCustomerProfileClient(MetaConnectedPageRepository pages, TokenCipher cipher, MetaProperties properties,
            ProfileLookupProperties lookup) {
        this(pages, cipher, properties, RestClient.builder().requestFactory(timeouts(lookup)));
    }

    /** The builder is a parameter so tests can bind a mock server to it. */
    MessengerCustomerProfileClient(MetaConnectedPageRepository pages, TokenCipher cipher, MetaProperties properties,
            RestClient.Builder restClients) {
        this.pages = pages;
        this.cipher = cipher;
        this.graph = restClients.baseUrl(GRAPH_HOST + properties.graphVersion()).build();
    }

    static SimpleClientHttpRequestFactory timeouts(ProfileLookupProperties lookup) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(lookup.timeout());
        factory.setReadTimeout(lookup.timeout());
        return factory;
    }

    @Override
    public Channel channel() {
        return Channel.MESSENGER;
    }

    @Override
    public Optional<String> fetchName(NamelessCustomer customer) {
        String token = cipher.decrypt(pageOf(customer).getPageAccessTokenEncrypted());
        return get(graph.get().uri(uri -> uri.path("/{psid}").queryParam("fields", "name")
                .build(customer.externalId())).headers(GraphAuth.bearer(token)))
                .map(GraphProfile::name).filter(name -> !name.isBlank());
    }

    private MetaConnectedPage pageOf(NamelessCustomer customer) {
        if (customer.metaPageId() != null) {
            return pages.findByIdAndOrganizationId(customer.metaPageId(), customer.organizationId())
                    .orElseThrow(ChannelNotConnectedException::new);
        }
        return GraphMessagingClient.onlyAccount(
                pages.findByOrganizationId(customer.organizationId(), PageRequest.of(0, 2)).getContent());
    }
}
