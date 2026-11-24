package com.connecta.backend.auth.service;

/** The new password is the same as the current one. */
public class PasswordUnchangedException extends RuntimeException {

    public PasswordUnchangedException() {
        super("The new password must differ from the current one");
    }
}
