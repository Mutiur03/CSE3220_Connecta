package com.connecta.backend.meta.service;

/** The OAuth callback's state is missing, altered, expired or malformed. */
public class InvalidOAuthStateException extends RuntimeException {

    public InvalidOAuthStateException() {
        super("Invalid or expired authorization state");
    }
}
