package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.AttachmentProperties;
import com.connecta.backend.file.service.PublicBaseUrlMissingException;
import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.meta.service.AttachmentChannelClient;
import com.connecta.backend.meta.service.AttachmentChannelClientFactory;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.MessagingWindowClosedException;
import com.connecta.backend.meta.service.UnsupportedAttachmentException;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.NotAMemberException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OutgoingAttachmentServiceTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};
    private static final long MAX_BYTES = 16;
    private static final String URL = "https://api.example.com/api/files/";

    @Mock
    ConversationDetailService conversations;

    @Mock
    AttachmentChannelClientFactory channels;

    @Mock
    AttachmentChannelClient client;

    @Mock
    StoredFileService files;

    @Mock
    OutgoingMessageRecorder recorder;

    OutgoingAttachmentService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID fileId = UUID.randomUUID();
    private final String subject = UUID.randomUUID().toString();
    private final Conversation thread = new Conversation(new Organization("Acme", "acme"), Channel.MESSENGER,
            "psid-1");
    private final OutgoingAttachment photo = new OutgoingAttachment(AttachmentKind.IMAGE, URL + fileId, "cat.png",
            "image/png");
    private final MessageResponse stored = new MessageResponse(UUID.randomUUID(), MessageDirection.OUTBOUND,
            "[Photo]", null, UUID.randomUUID(), "Agent", List.of());

    @BeforeEach
    void setUp() {
        service = new OutgoingAttachmentService(conversations,
                new AttachmentValidator(new AttachmentProperties(MAX_BYTES)), channels, files, recorder);
    }

    private void conversationFound() {
        when(conversations.find(subject, organizationId, conversationId)).thenReturn(thread);
    }

    private void channelSupports(AttachmentKind kind) {
        when(channels.forChannel(Channel.MESSENGER)).thenReturn(client);
        when(client.supports(kind)).thenReturn(true);
    }

    private void fileStored() {
        when(files.store(organizationId, PNG, "image/png", "cat.png")).thenReturn(fileId);
        when(files.publicUrl(fileId)).thenReturn(URL + fileId);
    }

    private MessageResponse send(byte[] bytes, String contentType, String fileName) {
        return service.send(subject, organizationId, conversationId, new AttachmentUpload(bytes, contentType,
                fileName));
    }

    @Nested
    class Send {

        @Test
        void validatesStoresSendsTheLinkThenRecordsInThatOrder() {
            conversationFound();
            channelSupports(AttachmentKind.IMAGE);
            fileStored();
            when(client.sendAttachment(thread, AttachmentKind.IMAGE, URL + fileId)).thenReturn("mid.1");
            when(recorder.recordAttachment(subject, organizationId, conversationId, "mid.1", photo))
                    .thenReturn(stored);

            MessageResponse sent = send(PNG, "image/png", "cat.png");

            InOrder order = inOrder(conversations, files, client, recorder);
            order.verify(conversations).find(subject, organizationId, conversationId);
            order.verify(files).store(organizationId, PNG, "image/png", "cat.png");
            order.verify(client).sendAttachment(thread, AttachmentKind.IMAGE, URL + fileId);
            order.verify(recorder).recordAttachment(subject, organizationId, conversationId, "mid.1", photo);
            assertEquals(stored, sent);
        }

        @Test
        void keepsOnlyTheLastPathSegmentOfTheFileName() {
            conversationFound();
            channelSupports(AttachmentKind.IMAGE);
            fileStored();
            when(client.sendAttachment(any(), any(), any())).thenReturn("mid.1");
            when(recorder.recordAttachment(subject, organizationId, conversationId, "mid.1", photo))
                    .thenReturn(stored);

            assertEquals(stored, send(PNG, "image/png", "C:\\Users\\ann\\cat.png"));
        }

        @Test
        void theServiceIsNotTransactional() throws NoSuchMethodException {
            assertNull(OutgoingAttachmentService.class.getAnnotation(Transactional.class));
            assertNull(OutgoingAttachmentService.class.getMethod("send", String.class, UUID.class, UUID.class,
                    AttachmentUpload.class).getAnnotation(Transactional.class));
        }

        @Test
        void anEchoCommittedFirstIsAttributedInsteadOfDuplicated() {
            conversationFound();
            channelSupports(AttachmentKind.IMAGE);
            fileStored();
            when(client.sendAttachment(any(), any(), any())).thenReturn("mid.1");
            when(recorder.recordAttachment(any(), any(), any(), any(), any()))
                    .thenThrow(new DataIntegrityViolationException("uq_messages_external"));
            when(recorder.attributeStored(subject, organizationId, "mid.1", photo)).thenReturn(Optional.of(stored));

            assertEquals(stored, send(PNG, "image/png", "cat.png"));
        }
    }

    @Nested
    class Validation {

        @Test
        void anEmptyFileIsRejectedBeforeAnythingIsStored() {
            conversationFound();

            assertThrows(EmptyAttachmentException.class, () -> send(new byte[0], "image/png", "x.png"));

            verifyNoInteractions(files, channels, recorder);
        }

        @Test
        void aFileOverTheLimitIsRejected() {
            conversationFound();

            assertThrows(AttachmentTooLargeException.class,
                    () -> send(new byte[(int) MAX_BYTES + 1], "image/png", "x.png"));

            verifyNoInteractions(files, channels, recorder);
        }

        @Test
        void aTypeOutsideTheAllowListIsRejected() {
            conversationFound();

            assertThrows(UnsupportedAttachmentTypeException.class, () -> send(PNG, "image/svg+xml", "x.svg"));

            verifyNoInteractions(files, channels, recorder);
        }

        @Test
        void contentThatDoesNotMatchTheDeclaredTypeIsRejected() {
            conversationFound();

            assertThrows(UnsupportedAttachmentTypeException.class, () -> send(PDF, "image/png", "x.png"));

            verifyNoInteractions(files, channels, recorder);
        }

        @Test
        void aKindTheChannelCannotDeliverIsRefusedBeforeStoring() {
            conversationFound();
            when(channels.forChannel(Channel.MESSENGER)).thenReturn(client);
            when(client.supports(AttachmentKind.FILE)).thenReturn(false);

            assertThrows(UnsupportedAttachmentException.class, () -> send(PDF, "application/pdf", "a.pdf"));

            verifyNoInteractions(files, recorder);
            verify(client, never()).sendAttachment(any(), any(), any());
        }

        @Test
        void aNonMemberSendsNothing() {
            when(conversations.find(subject, organizationId, conversationId)).thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class, () -> send(PNG, "image/png", "cat.png"));

            verifyNoInteractions(files, channels, recorder);
        }
    }

    @Nested
    class Cleanup {

        @Test
        void aMetaFailureDeletesTheStoredFileAndRecordsNothing() {
            conversationFound();
            channelSupports(AttachmentKind.IMAGE);
            fileStored();
            when(client.sendAttachment(any(), any(), any())).thenThrow(new ChannelSendFailedException("boom"));

            assertThrows(ChannelSendFailedException.class, () -> send(PNG, "image/png", "cat.png"));

            verify(files).delete(organizationId, fileId);
            verifyNoInteractions(recorder);
        }

        @Test
        void aClosedReplyWindowDeletesTheStoredFile() {
            conversationFound();
            channelSupports(AttachmentKind.IMAGE);
            fileStored();
            when(client.sendAttachment(any(), any(), any())).thenThrow(new MessagingWindowClosedException());

            assertThrows(MessagingWindowClosedException.class, () -> send(PNG, "image/png", "cat.png"));

            verify(files).delete(organizationId, fileId);
        }

        @Test
        void aMissingPublicAddressDeletesTheFileAndSendsNothing() {
            conversationFound();
            channelSupports(AttachmentKind.IMAGE);
            when(files.store(any(), any(), any(), any())).thenReturn(fileId);
            when(files.publicUrl(fileId)).thenThrow(new PublicBaseUrlMissingException());

            assertThrows(PublicBaseUrlMissingException.class, () -> send(PNG, "image/png", "cat.png"));

            verify(files).delete(organizationId, fileId);
            verify(client, never()).sendAttachment(any(), any(), any());
        }

        @Test
        void aFailedCleanupKeepsTheOriginalFailure() {
            conversationFound();
            channelSupports(AttachmentKind.IMAGE);
            fileStored();
            ChannelSendFailedException failure = new ChannelSendFailedException("boom");
            when(client.sendAttachment(any(), any(), any())).thenThrow(failure);
            IllegalStateException cleanup = new IllegalStateException("db down");
            doThrow(cleanup).when(files).delete(organizationId, fileId);

            ChannelSendFailedException thrown = assertThrows(ChannelSendFailedException.class,
                    () -> send(PNG, "image/png", "cat.png"));

            assertEquals(failure, thrown);
            assertEquals(List.of(cleanup), List.of(thrown.getSuppressed()));
        }
    }
}
