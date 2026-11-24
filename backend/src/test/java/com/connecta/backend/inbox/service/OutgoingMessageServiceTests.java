package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.meta.service.ChannelClient;
import com.connecta.backend.meta.service.ChannelClientFactory;
import com.connecta.backend.meta.service.ChannelNotConnectedException;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.MessagingWindowClosedException;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.NotAMemberException;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class OutgoingMessageServiceTests {

    @Mock
    ConversationDetailService conversations;

    @Mock
    ChannelClientFactory channels;

    @Mock
    ChannelClient client;

    @Mock
    OutgoingMessageRecorder recorder;

    OutgoingMessageService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private final String subject = UUID.randomUUID().toString();
    private final Conversation thread = new Conversation(organization, Channel.MESSENGER, "psid-1");
    private final MessageResponse stored = new MessageResponse(UUID.randomUUID(), MessageDirection.OUTBOUND,
            "hello", null, UUID.randomUUID(), "Agent");

    @BeforeEach
    void setUp() {
        service = new OutgoingMessageService(conversations, channels, recorder);
    }

    private void conversationFound() {
        when(conversations.find(subject, organizationId, conversationId)).thenReturn(thread);
        when(channels.forChannel(Channel.MESSENGER)).thenReturn(client);
    }

    @Nested
    class Send {

        @Test
        void loadsTheConversationSendsThroughMetaThenRecordsInThatOrder() {
            conversationFound();
            when(client.send(organizationId, "psid-1", "hello")).thenReturn("mid.1");
            when(recorder.record(subject, organizationId, conversationId, "mid.1", "hello")).thenReturn(stored);

            MessageResponse sent = service.send(subject, organizationId, conversationId, "hello");

            InOrder order = inOrder(conversations, client, recorder);
            order.verify(conversations).find(subject, organizationId, conversationId);
            order.verify(client).send(organizationId, "psid-1", "hello");
            order.verify(recorder).record(subject, organizationId, conversationId, "mid.1", "hello");
            assertEquals(stored, sent);
        }

        @Test
        void callsMetaWithoutAnActiveTransaction() {
            conversationFound();
            when(client.send(any(), any(), any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return "mid.1";
            });
            when(recorder.record(any(), any(), any(), any(), any())).thenReturn(stored);

            service.send(subject, organizationId, conversationId, "hello");

            verify(client).send(organizationId, "psid-1", "hello");
        }

        @Test
        void theOrchestratorIsNotTransactionalAndTheSeparateRecorderIs() throws NoSuchMethodException {
            assertNull(OutgoingMessageService.class.getAnnotation(Transactional.class));
            assertNull(OutgoingMessageService.class
                    .getMethod("send", String.class, UUID.class, UUID.class, String.class)
                    .getAnnotation(Transactional.class));
            assertEquals(Transactional.class, OutgoingMessageRecorder.class
                    .getMethod("record", String.class, UUID.class, UUID.class, String.class, String.class)
                    .getAnnotation(Transactional.class).annotationType());
        }
    }

    @Nested
    class DuplicateFromEcho {

        @Test
        void aConstraintViolationFallsBackToTheStoredRowInANewTransaction() {
            conversationFound();
            when(client.send(any(), any(), any())).thenReturn("mid.1");
            when(recorder.record(subject, organizationId, conversationId, "mid.1", "hello"))
                    .thenThrow(new DataIntegrityViolationException("uq_messages_external"));
            when(recorder.attributeStored(subject, organizationId, "mid.1")).thenReturn(Optional.of(stored));

            assertEquals(stored, service.send(subject, organizationId, conversationId, "hello"));
        }

        @Test
        void aViolationWithNoStoredRowIsRethrown() {
            conversationFound();
            when(client.send(any(), any(), any())).thenReturn("mid.1");
            DataIntegrityViolationException other = new DataIntegrityViolationException("not null");
            when(recorder.record(any(), any(), any(), any(), any())).thenThrow(other);
            when(recorder.attributeStored(subject, organizationId, "mid.1")).thenReturn(Optional.empty());

            DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
                    () -> service.send(subject, organizationId, conversationId, "hello"));

            assertSame(other, thrown);
        }
    }

    @Nested
    class Failures {

        @Test
        void aNonMemberSendsNothing() {
            when(conversations.find(subject, organizationId, conversationId)).thenThrow(new NotAMemberException());

            assertThrows(NotAMemberException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(channels, never()).forChannel(any());
            verify(recorder, never()).record(any(), any(), any(), any(), any());
        }

        @Test
        void anotherTenantsConversationIsNotFound() {
            when(conversations.find(subject, organizationId, conversationId))
                    .thenThrow(new ConversationNotFoundException());

            assertThrows(ConversationNotFoundException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(recorder, never()).record(any(), any(), any(), any(), any());
        }

        @Test
        void aChannelWithoutAConnectedAccountStoresNothing() {
            conversationFound();
            when(client.send(any(), any(), any())).thenThrow(new ChannelNotConnectedException());

            assertThrows(ChannelNotConnectedException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(recorder, never()).record(any(), any(), any(), any(), any());
        }

        @Test
        void aMetaFailureStoresNothing() {
            conversationFound();
            when(client.send(any(), any(), any())).thenThrow(new ChannelSendFailedException("boom"));

            assertThrows(ChannelSendFailedException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(recorder, never()).record(any(), any(), any(), any(), any());
            verify(recorder, never()).attributeStored(any(), any(), any());
        }

        @Test
        void aClosedReplyWindowStoresNothing() {
            conversationFound();
            when(client.send(any(), any(), any())).thenThrow(new MessagingWindowClosedException());

            assertThrows(MessagingWindowClosedException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(recorder, never()).record(any(), any(), any(), any(), any());
        }
    }
}
