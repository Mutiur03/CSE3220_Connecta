package com.connecta.backend.inbox.service;

/** Why a media download failed. Logged instead of the URL, which may carry an access token. */
public enum MediaDownloadFailure {
    INVALID_URL,
    NOT_HTTPS,
    HOST_NOT_ALLOWED,
    UNRESOLVABLE_HOST,
    PRIVATE_ADDRESS,
    TOO_MANY_REDIRECTS,
    HTTP_ERROR,
    TOO_LARGE,
    EMPTY,
    NETWORK
}
