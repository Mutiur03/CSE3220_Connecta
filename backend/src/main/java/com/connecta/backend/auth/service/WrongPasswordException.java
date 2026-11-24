package com.connecta.backend.auth.service;

/** The current password given for a password change does not match. */
public class WrongPasswordException extends RuntimeException {

    public WrongPasswordException() {
        super("The current password is incorrect");
    }
}
