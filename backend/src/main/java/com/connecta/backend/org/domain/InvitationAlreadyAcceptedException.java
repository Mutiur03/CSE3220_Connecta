package com.connecta.backend.org.domain;

/** The invitation was accepted earlier, so it cannot be accepted or revoked again. */
public class InvitationAlreadyAcceptedException extends RuntimeException {

    public InvitationAlreadyAcceptedException() {
        super("This invitation has already been accepted");
    }
}
