package com.connecta.backend.auth.service;

/** The refresh token is unknown, expired, revoked or belongs to a disabled user. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("Invalid or expired refresh token");
    }
}
