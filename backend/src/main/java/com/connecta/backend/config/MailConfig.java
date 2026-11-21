package com.connecta.backend.config;

import com.connecta.backend.mail.service.InvitationMailer;
import com.connecta.backend.mail.service.NoopInvitationMailer;
import com.connecta.backend.mail.service.SmtpInvitationMailer;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Chooses the mailer once, at startup: SMTP when a host is configured, otherwise a no-op. The mail sender is
 * built here, not by Spring Boot's auto-configuration, so a blank SMTP_HOST can never half-configure it.
 */
@Configuration
class MailConfig {

    private static final int TIMEOUT_MILLIS = 10_000;

    @Bean
    InvitationMailer invitationMailer(MailProperties props) {
        if (!props.smtpConfigured()) {
            return new NoopInvitationMailer();
        }
        return new SmtpInvitationMailer(javaMailSender(props), props.from());
    }

    private static JavaMailSender javaMailSender(MailProperties props) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(props.host());
        sender.setPort(props.port());
        boolean authenticated = props.username() != null && !props.username().isBlank();
        if (authenticated) {
            sender.setUsername(props.username());
            sender.setPassword(props.password());
        }
        Properties mail = sender.getJavaMailProperties();
        mail.put("mail.smtp.auth", String.valueOf(authenticated));
        mail.put("mail.smtp.starttls.enable", String.valueOf(props.starttls()));
        mail.put("mail.smtp.connectiontimeout", String.valueOf(TIMEOUT_MILLIS));
        mail.put("mail.smtp.timeout", String.valueOf(TIMEOUT_MILLIS));
        mail.put("mail.smtp.writetimeout", String.valueOf(TIMEOUT_MILLIS));
        return sender;
    }
}
