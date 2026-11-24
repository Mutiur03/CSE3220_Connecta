package com.connecta.backend.auth.service;

/** The refresh token is unknown, expired, revoked or belongs to a disabled user. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        this("Invalid or expired refresh token");
    }

    protected InvalidTokenException(String message) {
        super(message);
    }
}
