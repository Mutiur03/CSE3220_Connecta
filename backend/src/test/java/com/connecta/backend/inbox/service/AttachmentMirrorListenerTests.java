package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.MessageDirection;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachmentMirrorListenerTests {

    private static final UUID ORG = UUID.randomUUID();
    private static final UUID MESSAGE = UUID.randomUUID();

    private AttachmentMirrorService mirror;
    private AttachmentMirrorListener listener;

    @BeforeEach
    void setUp() {
        mirror = mock(AttachmentMirrorService.class);
        listener = new AttachmentMirrorListener(mirror);
    }

    private static MessageReceivedEvent received() {
        return new MessageReceivedEvent(ORG, UUID.randomUUID(), MESSAGE, Channel.MESSENGER,
                MessageDirection.INBOUND, false);
    }

    @Test
    void aReceivedMessageHasItsAttachmentsCopied() {
        listener.onMessageReceived(received());

        verify(mirror).mirrorMessage(ORG, MESSAGE);
    }

    @Test
    void aHistoryImportCopiesOneBatchOfItsOrganization() {
        listener.onHistoryImported(new HistoryImportedEvent(ORG));

        verify(mirror).mirrorPending(ORG);
    }

    @Test
    void aFailureNeverReachesThePublisher() {
        when(mirror.mirrorMessage(ORG, MESSAGE)).thenThrow(new IllegalStateException("db down"));
        when(mirror.mirrorPending(ORG)).thenThrow(new IllegalStateException("db down"));

        assertDoesNotThrow(() -> listener.onMessageReceived(received()));
        assertDoesNotThrow(() -> listener.onHistoryImported(new HistoryImportedEvent(ORG)));
    }
}
