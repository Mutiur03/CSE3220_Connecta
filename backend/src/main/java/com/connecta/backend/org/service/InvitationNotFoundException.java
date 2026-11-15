package com.connecta.backend.org.service;

/**
 * No invitation matches: an unknown id or token, another organization's invitation, or a token addressed
 * to someone else. All cases share one message, so a 404 never reveals which one applied.
 */
public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException() {
        super("Invitation not found");
    }
}
