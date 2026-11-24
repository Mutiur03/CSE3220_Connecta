package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.config.ProfileLookupProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.NamelessCustomer;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapter: reads an Instagram user's name with the account token (Graph {@code GET /{igsid}?fields=name,username}).
 * Instagram often has no name for a person, so the username is the fallback.
 */
@Component
class InstagramCustomerProfileClient extends GraphCustomerProfileClient {

    private static final String GRAPH_HOST = "https://graph.instagram.com/";

    private final MetaConnectedAccountRepository accounts;
    private final TokenCipher cipher;
    private final RestClient graph;

    @Autowired
    InstagramCustomerProfileClient(MetaConnectedAccountRepository accounts, TokenCipher cipher,
            MetaProperties properties, ProfileLookupProperties lookup) {
        this(accounts, cipher, properties,
                RestClient.builder().requestFactory(MessengerCustomerProfileClient.timeouts(lookup)));
    }

    /** The builder is a parameter so tests can bind a mock server to it. */
    InstagramCustomerProfileClient(MetaConnectedAccountRepository accounts, TokenCipher cipher,
            MetaProperties properties, RestClient.Builder restClients) {
        this.accounts = accounts;
        this.cipher = cipher;
        this.graph = restClients.baseUrl(GRAPH_HOST + properties.graphVersion()).build();
    }

    @Override
    public Channel channel() {
        return Channel.INSTAGRAM;
    }

    @Override
    public Optional<String> fetchName(NamelessCustomer customer) {
        String token = cipher.decrypt(accountOf(customer).getAccessTokenEncrypted());
        return get(graph.get().uri(uri -> uri.path("/{igsid}").queryParam("fields", "name,username")
                .build(customer.externalId())).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .flatMap(profile -> firstNonBlank(profile.name(), profile.username()));
    }

    private static Optional<String> firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private MetaConnectedAccount accountOf(NamelessCustomer customer) {
        if (customer.instagramAccountId() != null) {
            return accounts.findByIdAndOrganizationId(customer.instagramAccountId(), customer.organizationId())
                    .orElseThrow(ChannelNotConnectedException::new);
        }
        return GraphMessagingClient.onlyAccount(accounts.findByOrganizationIdAndPlatform(customer.organizationId(),
                MetaConnectedAccount.PLATFORM_INSTAGRAM, PageRequest.of(0, 2)).getContent());
    }
}
