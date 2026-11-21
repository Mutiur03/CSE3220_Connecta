package com.connecta.backend.mail.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpInvitationMailerTests {

    @Mock
    JavaMailSender sender;

    @Test
    void sendsAPlainTextMailWithTheLinkTheInviterAndTheRole() {
        InvitationMail mail = new InvitationMail("newbie@example.com", "Acme", "Jane Admin", "AGENT",
                "https://app.example.com/invitations/accept?token=tok-123");

        new SmtpInvitationMailer(sender, "connecta@example.com").send(mail);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        SimpleMailMessage message = sent.getValue();
        assertEquals("connecta@example.com", message.getFrom());
        assertArrayEquals(new String[] {"newbie@example.com"}, message.getTo());
        assertEquals("You are invited to Acme on Connecta", message.getSubject());
        assertTrue(message.getText().contains("Jane Admin invited you to join Acme on Connecta as agent"));
        assertTrue(message.getText().contains("https://app.example.com/invitations/accept?token=tok-123"));
    }

    @Test
    void theNoopMailerSendsNothingAndNeverFails() {
        assertDoesNotThrow(() -> new NoopInvitationMailer()
                .send(new InvitationMail("a@b.co", "Acme", "Jane", "AGENT", "https://x/?token=t")));
    }
}
