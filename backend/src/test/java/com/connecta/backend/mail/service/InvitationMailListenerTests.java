package com.connecta.backend.mail.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.connecta.backend.config.MailProperties;
import com.connecta.backend.org.service.InvitationCreatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationMailListenerTests {

    private static final InvitationCreatedEvent EVENT = new InvitationCreatedEvent(UUID.randomUUID(),
            UUID.randomUUID(), "Acme", "Jane Admin", "newbie@example.com", "AGENT", "tok-123");

    @Mock
    InvitationMailer mailer;

    private InvitationMailListener listener(String frontendUrl) {
        return new InvitationMailListener(mailer,
                new MailProperties("localhost", 1025, "", "", false, "connecta@test.local", frontendUrl));
    }

    @Test
    void sendsTheInvitationWithALinkToTheAcceptPage() {
        listener("https://app.example.com").onInvitationCreated(EVENT);

        ArgumentCaptor<InvitationMail> mail = ArgumentCaptor.forClass(InvitationMail.class);
        verify(mailer).send(mail.capture());
        assertEquals("newbie@example.com", mail.getValue().to());
        assertEquals("Acme", mail.getValue().organizationName());
        assertEquals("Jane Admin", mail.getValue().inviterName());
        assertEquals("AGENT", mail.getValue().role());
        assertEquals("https://app.example.com/invitations/accept?token=tok-123", mail.getValue().acceptUrl());
    }

    @Test
    void aTrailingSlashOnTheFrontendUrlDoesNotDoubleUp() {
        listener("https://app.example.com/").onInvitationCreated(EVENT);

        ArgumentCaptor<InvitationMail> mail = ArgumentCaptor.forClass(InvitationMail.class);
        verify(mailer).send(mail.capture());
        assertEquals("https://app.example.com/invitations/accept?token=tok-123", mail.getValue().acceptUrl());
    }

    @Test
    void aFailedSendIsSwallowedSoTheInvitationStands() {
        doThrow(new IllegalStateException("smtp down")).when(mailer).send(any());

        assertDoesNotThrow(() -> listener("https://app.example.com").onInvitationCreated(EVENT));
    }

    @Test
    void neitherTheEventNorTheMailPrintsTheToken() {
        assertFalse(EVENT.toString().contains("tok-123"));
        InvitationMail mail = new InvitationMail("a@b.co", "Acme", "Jane", "AGENT", "https://x/?token=tok-123");
        assertFalse(mail.toString().contains("tok-123"));
    }
}
