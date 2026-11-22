package com.connecta.backend.meta.service;

import com.connecta.backend.config.MetaProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
    private static final String PAGES_PATH = "/me/accounts";
    private static final String SUBSCRIBED_APPS_PATH = "/{pageId}/subscribed_apps";
    private static final String SUBSCRIBED_FIELDS = "messages,message_echoes,message_reactions,message_edits";

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

    @Override
    public List<MetaPage> listPages(String userAccessToken) {
        PagesResponse body;
        try {
            body = graph.get()
                    .uri(uri -> uri.path(PAGES_PATH)
                            .queryParam("fields", "id,name,access_token")
                            .queryParam("access_token", userAccessToken)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(PagesResponse.class);
        } catch (RestClientException e) {
            log.warn("Meta page list request failed: {}", e.getClass().getSimpleName());
            throw new MetaAuthorizationFailedException("Meta page list request failed", e);
        }
        if (body == null) {
            throw new MetaAuthorizationFailedException("Meta returned no page list");
        }
        return body.data() == null ? List.of() : body.data();
    }

    @Override
    public void subscribePage(String pageId, String pageAccessToken) {
        SuccessResponse body;
        try {
            body = graph.post()
                    .uri(uri -> uri.path(SUBSCRIBED_APPS_PATH)
                            .queryParam("subscribed_fields", SUBSCRIBED_FIELDS)
                            .queryParam("access_token", pageAccessToken)
                            .build(pageId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SuccessResponse.class);
        } catch (RestClientException e) {
            log.warn("Meta page subscribe request failed: {}", e.getClass().getSimpleName());
            throw new MetaAuthorizationFailedException("Meta page subscribe request failed", e);
        }
        if (body == null || !body.success()) {
            throw new MetaAuthorizationFailedException("Meta did not subscribe the page");
        }
    }

    private Instant expiry(Long expiresInSeconds) {
        return expiresInSeconds == null ? null : clock.instant().plusSeconds(expiresInSeconds);
    }

    /** The subset of Meta's token response this flow reads. */
    record TokenResponse(@JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    /** Meta's /me/accounts response: the Pages the user granted. */
    record PagesResponse(List<MetaPage> data) {
    }

    /** Meta's subscription response. */
    record SuccessResponse(boolean success) {
    }
}
