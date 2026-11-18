package com.connecta.backend.mail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when SMTP is not configured. Logs that nothing was sent, never the address, token or link. */
public class NoopInvitationMailer implements InvitationMailer {

    private static final Logger log = LoggerFactory.getLogger(NoopInvitationMailer.class);

    @Override
    public void send(InvitationMail mail) {
        log.info("Invitation email not sent: SMTP is not configured");
    }
}
