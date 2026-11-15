package com.connecta.backend.org.domain;

/** The invitation expired or was revoked, so it can no longer be accepted. */
public class InvitationNotAcceptableException extends RuntimeException {

    public InvitationNotAcceptableException() {
        super("This invitation has expired or was revoked");
    }
}
