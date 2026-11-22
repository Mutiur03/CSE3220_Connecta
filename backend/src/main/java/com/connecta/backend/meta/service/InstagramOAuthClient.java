package com.connecta.backend.meta.service;

/**
 * The boundary to Instagram's Business Login endpoints (api.instagram.com and graph.instagram.com). The service
 * depends on this interface only, so the HTTP details stay in {@link GraphInstagramOAuthClient}.
 */
public interface InstagramOAuthClient {

    /** The Instagram login URL that starts the authorization, carrying the signed state. */
    String authorizationUrl(String state, String redirectUri);

    /** Trades the code for a short-lived token. */
    MetaOAuthClient.MetaUserToken exchangeCode(String code, String redirectUri);

    /** Trades a short-lived token for a long-lived one (about 60 days). */
    MetaOAuthClient.MetaUserToken extendToken(String shortLivedToken);

    /** The professional account the long-lived token belongs to. */
    InstagramAccount fetchAccount(String longLivedToken);

    record InstagramAccount(String userId, String username) {
    }
}
