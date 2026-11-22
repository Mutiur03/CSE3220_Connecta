package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.util.function.UnaryOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adapter for Meta's Graph API and the Facebook login dialog (Facebook Login for Business). It holds the app
 * secret and calls Meta from the server only. A failed call is logged with its status, and callers get a
 * {@link MetaAuthorizationFailedException} without Meta's error text.
 */
@Slf4j
@Component
class GraphMetaOAuthClient implements MetaOAuthClient {

    private static final String DIALOG_HOST = "https://www.facebook.com/";
    private static final String GRAPH_HOST = "https://graph.facebook.com/";
    private static final String TOKEN_PATH = "/oauth/access_token";
    private static final String GRANT_FB_EXCHANGE = "fb_exchange_token";

    private final MetaProperties properties;
    private final RestClient graph;
    private final Clock clock;

    @Autowired
    GraphMetaOAuthClient(MetaProperties properties, Clock clock) {
        this(properties, RestClient.builder(), clock);
    }

    /** The builder is a parameter so tests can bind a mock server to it; production uses the default builder. */
    GraphMetaOAuthClient(MetaProperties properties, RestClient.Builder restClients, Clock clock) {
        this.properties = properties;
        this.graph = restClients.baseUrl(GRAPH_HOST + properties.graphVersion()).build();
        this.clock = clock;
    }

    @Override
    public String authorizationUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(DIALOG_HOST + properties.graphVersion() + "/dialog/oauth")
                .queryParam("client_id", properties.appId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("config_id", properties.configId())
                .queryParam("response_type", "code")
                .queryParam("override_default_response_type", "true")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public MetaUserToken exchangeCode(String code, String redirectUri) {
        return requestToken(uri -> uri
                .queryParam("client_id", properties.appId())
                .queryParam("client_secret", properties.appSecret())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code", code));
    }

    @Override
    public MetaUserToken extendToken(String shortLivedToken) {
        return requestToken(uri -> uri
                .queryParam("grant_type", GRANT_FB_EXCHANGE)
                .queryParam("client_id", properties.appId())
                .queryParam("client_secret", properties.appSecret())
                .queryParam("fb_exchange_token", shortLivedToken));
    }

    private MetaUserToken requestToken(UnaryOperator<UriBuilder> query) {
        TokenResponse body;
        try {
            body = graph.get()
                    .uri(uri -> query.apply(uri.path(TOKEN_PATH)).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientException e) {
            log.warn("Meta token request failed: {}", e.getClass().getSimpleName());
            throw new MetaAuthorizationFailedException("Meta token request failed", e);
        }
        if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
            throw new MetaAuthorizationFailedException("Meta returned no access token");
        }
        return new MetaUserToken(body.accessToken(), expiry(body.expiresIn()));
    }

    private Instant expiry(Long expiresInSeconds) {
        return expiresInSeconds == null ? null : clock.instant().plusSeconds(expiresInSeconds);
    }

    /** The subset of Meta's token response this flow reads. */
    record TokenResponse(@JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }
}
