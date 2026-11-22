package com.connecta.backend.meta.service;

import com.connecta.backend.config.InstagramProperties;
import com.connecta.backend.config.MetaProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Adapter for Instagram Business Login. Short-lived tokens come from api.instagram.com, long-lived tokens and the
 * account lookup from graph.instagram.com. The app secret stays on the server; failures are logged with their
 * status and reach callers as a generic {@link MetaAuthorizationFailedException}.
 */
@Slf4j
@Component
class GraphInstagramOAuthClient implements InstagramOAuthClient {

    private static final String LOGIN_HOST = "https://www.instagram.com/oauth/authorize";
    private static final String API_HOST = "https://api.instagram.com";
    private static final String GRAPH_HOST = "https://graph.instagram.com/";
    private static final String SCOPES = "instagram_business_basic,instagram_business_manage_messages";

    private final InstagramProperties instagram;
    private final RestClient api;
    private final RestClient graph;
    private final Clock clock;

    @Autowired
    GraphInstagramOAuthClient(InstagramProperties instagram, MetaProperties meta, Clock clock) {
        this(instagram, meta, RestClient.builder(), clock);
    }

    /** The builder is a parameter so tests can bind a mock server to it; production uses the default builder. */
    GraphInstagramOAuthClient(InstagramProperties instagram, MetaProperties meta, RestClient.Builder restClients,
            Clock clock) {
        this.instagram = instagram;
        this.api = restClients.clone().baseUrl(API_HOST).build();
        this.graph = restClients.clone().baseUrl(GRAPH_HOST + meta.graphVersion()).build();
        this.clock = clock;
    }

    @Override
    public String authorizationUrl(String state, String redirectUri) {
        return UriComponentsBuilder.fromUriString(LOGIN_HOST)
                .queryParam("client_id", instagram.appId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPES)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public MetaOAuthClient.MetaUserToken exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", instagram.appId());
        form.add("client_secret", instagram.appSecret());
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        ShortTokenResponse body = request(() -> api.post()
                .uri("/oauth/access_token")
                .body(form)
                .retrieve()
                .body(ShortTokenResponse.class));
        // Instagram answers either flat or wrapped in data[0], depending on the API version.
        String token = null;
        if (body != null) {
            ShortTokenEntry entry = body.data() != null && !body.data().isEmpty() ? body.data().get(0) : null;
            token = entry != null ? entry.accessToken() : body.accessToken();
        }
        if (token == null || token.isBlank()) {
            throw new MetaAuthorizationFailedException("Instagram returned no access token");
        }
        return new MetaOAuthClient.MetaUserToken(token, null);
    }

    @Override
    public MetaOAuthClient.MetaUserToken extendToken(String shortLivedToken) {
        GraphMetaOAuthClient.TokenResponse body = request(() -> graph.get()
                .uri(uri -> uri.path("/access_token")
                        .queryParam("grant_type", "ig_exchange_token")
                        .queryParam("client_secret", instagram.appSecret())
                        .queryParam("access_token", shortLivedToken)
                        .build())
                .retrieve()
                .body(GraphMetaOAuthClient.TokenResponse.class));
        if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
            throw new MetaAuthorizationFailedException("Instagram returned no long-lived token");
        }
        Instant expiresAt = body.expiresIn() == null ? null : clock.instant().plusSeconds(body.expiresIn());
        return new MetaOAuthClient.MetaUserToken(body.accessToken(), expiresAt);
    }

    @Override
    public InstagramAccount fetchAccount(String longLivedToken) {
        AccountResponse body = request(() -> graph.get()
                .uri(uri -> uri.path("/me")
                        .queryParam("fields", "user_id,username")
                        .queryParam("access_token", longLivedToken)
                        .build())
                .retrieve()
                .body(AccountResponse.class));
        if (body == null || body.userId() == null || body.userId().isBlank()) {
            throw new MetaAuthorizationFailedException("Instagram returned no account id");
        }
        return new InstagramAccount(body.userId(), body.username());
    }

    private static <T> T request(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException e) {
            log.warn("Instagram request failed: {}", e.getClass().getSimpleName());
            throw new MetaAuthorizationFailedException("Instagram request failed", e);
        }
    }

    /** Short-lived token response: flat, or wrapped in data (both shapes occur). */
    record ShortTokenResponse(@JsonProperty("data") List<ShortTokenEntry> data,
            @JsonProperty("access_token") String accessToken) {
    }

    record ShortTokenEntry(@JsonProperty("access_token") String accessToken) {
    }

    record AccountResponse(@JsonProperty("user_id") String userId, String username) {
    }
}
