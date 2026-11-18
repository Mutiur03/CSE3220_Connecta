package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.mail.service.NoopInvitationMailer;
import com.connecta.backend.mail.service.SmtpInvitationMailer;
import org.junit.jupiter.api.Test;

class MailConfigTests {

    private static MailProperties props(String host) {
        return new MailProperties(host, 1025, "", "", false, "connecta@test.local", "http://localhost:3000");
    }

    @Test
    void aBlankHostMeansNothingIsSent() {
        assertFalse(props("").smtpConfigured());
        assertFalse(props(null).smtpConfigured());
        assertInstanceOf(NoopInvitationMailer.class, new MailConfig().invitationMailer(props("  ")));
    }

    @Test
    void aConfiguredHostSendsOverSmtp() {
        assertTrue(props("localhost").smtpConfigured());
        assertInstanceOf(SmtpInvitationMailer.class, new MailConfig().invitationMailer(props("localhost")));
    }
}
