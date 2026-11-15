package com.connecta.backend.auth.dto;

/** Returned by login and refresh. expiresIn is the access token lifetime in seconds. */
public record TokenResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        String refreshToken,
        UserResponse user) {
}
