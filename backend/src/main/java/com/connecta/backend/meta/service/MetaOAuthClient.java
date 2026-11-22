package com.connecta.backend.meta.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * The boundary to Meta's OAuth endpoints (Graph API). The service depends on this interface only, so the
 * Graph HTTP details stay in one adapter ({@link GraphMetaOAuthClient}) and tests replace it.
 */
public interface MetaOAuthClient {

    /** The Pages the admin granted, each with its Page access token (GET /me/accounts). */
    List<MetaPage> listPages(String userAccessToken);

    /** Subscribes the app to a Page's messages and message echoes (POST /{page-id}/subscribed_apps). */
    void subscribePage(String pageId, String pageAccessToken);

    /** A Facebook Page from /me/accounts: its id, name and Page access token. */
    record MetaPage(String id, String name, @JsonProperty("access_token") String accessToken) {
    }

    /** The Meta login dialog URL that starts the authorization, carrying the signed state. */
    String authorizationUrl(String state, String redirectUri);

    /** Trades the code from the dialog for a short-lived user token. */
    MetaUserToken exchangeCode(String code, String redirectUri);

    /** Trades a short-lived user token for a long-lived one (about 60 days). */
    MetaUserToken extendToken(String shortLivedToken);

    /** A Meta user token and its expiry, or null when Meta did not state one. */
    record MetaUserToken(String accessToken, Instant expiresAt) {
    }
}
