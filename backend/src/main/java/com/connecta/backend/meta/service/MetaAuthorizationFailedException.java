package com.connecta.backend.meta.service;

/** Meta refused or could not complete an authorization step. The Graph error detail is logged, not returned. */
public class MetaAuthorizationFailedException extends RuntimeException {

    public MetaAuthorizationFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public MetaAuthorizationFailedException(String message) {
        super(message);
    }
}
