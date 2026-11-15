package com.connecta.backend.auth.service;

/** One message for every login failure, so responses never reveal whether an email exists. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
