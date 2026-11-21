package com.connecta.backend.mail.service;

import com.connecta.backend.config.MailProperties;
import com.connecta.backend.org.service.InvitationCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Observer of {@link InvitationCreatedEvent}: emails the invitation link once the invitation is committed.
 * A failed send is logged and swallowed on purpose: the invitation exists and its link is still returned
 * to the admin, so a mail outage must not undo it. The token is never logged.
 */
@Component
@RequiredArgsConstructor
public class InvitationMailListener {

    private static final Logger log = LoggerFactory.getLogger(InvitationMailListener.class);
    private static final String ACCEPT_PATH = "/invitations/accept?token=";

    private final InvitationMailer mailer;
    private final MailProperties props;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvitationCreated(InvitationCreatedEvent event) {
        InvitationMail mail = new InvitationMail(event.email(), event.organizationName(), event.inviterName(),
                event.role(), acceptUrl(event.token()));
        try {
            mailer.send(mail);
        } catch (RuntimeException e) {
            log.warn("Invitation {} email could not be sent: {}", event.invitationId(), e.getClass().getSimpleName());
        }
    }

    private String acceptUrl(String token) {
        String base = props.frontendUrl();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + ACCEPT_PATH + token;
    }
}
