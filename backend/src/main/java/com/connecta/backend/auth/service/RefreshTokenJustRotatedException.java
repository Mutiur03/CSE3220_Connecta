package com.connecta.backend.auth.service;

/**
 * The refresh token was rotated a moment ago, so this is a concurrent request from the same client
 * (two tabs, page and prefetch), not a replay. Nothing is revoked; the caller keeps its session and
 * picks up the new token from the request that won the race.
 */
public class RefreshTokenJustRotatedException extends InvalidTokenException {

    public RefreshTokenJustRotatedException() {
        super("Refresh token was rotated moments ago");
    }
}
