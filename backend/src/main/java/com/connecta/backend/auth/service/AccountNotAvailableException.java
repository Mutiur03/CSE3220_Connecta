package com.connecta.backend.auth.service;

/** A valid token whose account is gone, disabled, or whose subject is not a user id. */
public class AccountNotAvailableException extends RuntimeException {

    public AccountNotAvailableException() {
        super("Account is not available");
    }
}
