package com.connecta.backend.org.service;

public class DuplicateInvitationException extends RuntimeException {

    public DuplicateInvitationException() {
        super("This email already has a pending invitation to the organization");
    }
}
