package com.connecta.backend.mail.service;

/** Sends an invitation email. One implementation sends over SMTP, another sends nothing. */
public interface InvitationMailer {

    /** Throws a RuntimeException when the message cannot be sent; callers decide what to do about it. */
    void send(InvitationMail mail);
}
