package com.connecta.backend.mail.service;

/** Everything an invitation email says. {@code acceptUrl} carries the raw token, so never log this record. */
public record InvitationMail(String to, String organizationName, String inviterName, String role, String acceptUrl) {

    @Override
    public String toString() {
        return "InvitationMail[to=" + to + ", organization=" + organizationName + "]";
    }
}
