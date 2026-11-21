package com.connecta.backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Invitation email settings (app.mail.*), read from SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD,
 * SMTP_STARTTLS, MAIL_FROM and FRONTEND_URL. A blank host means no email is sent. frontendUrl is the
 * public address of the web app, used to build the link in the email.
 */
@Validated
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String host, int port, String username, String password, boolean starttls,
        @NotBlank String from, @NotBlank String frontendUrl) {

    public boolean smtpConfigured() {
        return host != null && !host.isBlank();
    }
}
