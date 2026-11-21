package com.connecta.backend.mail.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Sends the invitation as a plain-text email through an SMTP server. */
public class SmtpInvitationMailer implements InvitationMailer {

    private final JavaMailSender sender;
    private final String from;

    public SmtpInvitationMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(InvitationMail mail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(mail.to());
        message.setSubject("You are invited to " + mail.organizationName() + " on Connecta");
        message.setText(body(mail));
        sender.send(message);
    }

    private static String body(InvitationMail mail) {
        return mail.inviterName() + " invited you to join " + mail.organizationName() + " on Connecta as "
                + mail.role().toLowerCase() + ".\n\n"
                + "Accept the invitation:\n" + mail.acceptUrl() + "\n\n"
                + "If you were not expecting this, you can ignore this email.\n";
    }
}
