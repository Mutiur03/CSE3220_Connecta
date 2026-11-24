package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OutgoingMessageRecorderTests {

    @Mock
    ConversationRepository conversations;

    @Mock
    MessageRepository messages;

    @Mock
    ActiveCaller activeCaller;

    @Mock
    ApplicationEventPublisher publisher;

    OutgoingMessageRecorder recorder;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private final User agent = TestUsers.user("agent@example.com", Roles.AGENT);
    private final String subject = agent.getId().toString();
    private final Conversation thread = new Conversation(organization, Channel.MESSENGER, "psid-1");

    @BeforeEach
    void setUp() {
        TestUsers.set(organization, "id", organizationId);
        TestUsers.set(thread, "id", conversationId);
        recorder = new OutgoingMessageRecorder(conversations, messages, activeCaller, publisher,
                TestClock.FIXED);
        when(activeCaller.load(subject)).thenReturn(agent);
    }

    private Message echoed() {
        Message echoed = Message.outbound(thread, null, "mid.1", "hello", TestClock.NOW);
        TestUsers.set(echoed, "id", UUID.randomUUID());
        return echoed;
    }

    @Nested
    class Record {

        @Test
        void savesAnOutboundMessageWithMetasIdAndMovesTheLastMessageTime() {
            when(messages.findByOrganizationIdAndExternalMessageId(organizationId, "mid.1"))
                    .thenReturn(Optional.empty());
            when(conversations.findByIdAndOrganizationId(conversationId, organizationId))
                    .thenReturn(Optional.of(thread));
            when(messages.saveAndFlush(any(Message.class))).thenAnswer(call -> {
                Message message = call.getArgument(0);
                TestUsers.set(message, "id", UUID.randomUUID());
                return message;
            });

            MessageResponse sent = recorder.record(subject, organizationId, conversationId, "mid.1", "hello");

            ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
            verify(messages).saveAndFlush(saved.capture());
            assertEquals("mid.1", saved.getValue().getExternalMessageId());
            assertEquals(MessageDirection.OUTBOUND, saved.getValue().getDirection());
            assertEquals(agent, saved.getValue().getSentByUser());
            assertEquals(agent.getId(), sent.senderId());
            assertEquals(TestClock.NOW, sent.sentAt());
            assertEquals(TestClock.NOW, thread.getLastMessageAt());
        }

        @Test
        void publishesAnOutboundEventForLiveUpdates() {
            when(messages.findByOrganizationIdAndExternalMessageId(any(), any())).thenReturn(Optional.empty());
            when(conversations.findByIdAndOrganizationId(conversationId, organizationId))
                    .thenReturn(Optional.of(thread));
            when(messages.saveAndFlush(any(Message.class))).thenAnswer(call -> {
                Message message = call.getArgument(0);
                TestUsers.set(message, "id", UUID.randomUUID());
                return message;
            });

            recorder.record(subject, organizationId, conversationId, "mid.1", "hello");

            ArgumentCaptor<MessageReceivedEvent> event = ArgumentCaptor.forClass(MessageReceivedEvent.class);
            verify(publisher).publishEvent(event.capture());
            assertEquals(MessageDirection.OUTBOUND, event.getValue().direction());
            assertEquals(Channel.MESSENGER, event.getValue().channel());
            assertEquals(false, event.getValue().newConversation());
            assertEquals(conversationId, event.getValue().conversationId());
            assertEquals(organizationId, event.getValue().organizationId());
        }

        @Test
        void anEchoStoredFirstIsAttributedWithoutASecondRowAndRepublished() {
            Message echoed = echoed();
            when(messages.findByOrganizationIdAndExternalMessageId(organizationId, "mid.1"))
                    .thenReturn(Optional.of(echoed));

            MessageResponse sent = recorder.record(subject, organizationId, conversationId, "mid.1", "hello");

            verify(messages, never()).saveAndFlush(any());
            assertEquals(echoed.getId(), sent.id());
            assertEquals(agent.getId(), sent.senderId());
            ArgumentCaptor<MessageReceivedEvent> event = ArgumentCaptor.forClass(MessageReceivedEvent.class);
            verify(publisher).publishEvent(event.capture());
            assertEquals(echoed.getId(), event.getValue().messageId());
            assertEquals(MessageDirection.OUTBOUND, event.getValue().direction());
            assertEquals(false, event.getValue().newConversation());
        }

        @Test
        void anEchoThatAlreadyHasASenderIsNotRepublished() {
            Message echoed = echoed();
            echoed.attributeTo(agent);
            when(messages.findByOrganizationIdAndExternalMessageId(organizationId, "mid.1"))
                    .thenReturn(Optional.of(echoed));

            recorder.record(subject, organizationId, conversationId, "mid.1", "hello");

            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void aConstraintViolationOnSaveIsNotSwallowed() {
            when(messages.findByOrganizationIdAndExternalMessageId(any(), any())).thenReturn(Optional.empty());
            when(conversations.findByIdAndOrganizationId(conversationId, organizationId))
                    .thenReturn(Optional.of(thread));
            when(messages.saveAndFlush(any(Message.class))).thenThrow(new DataIntegrityViolationException("dup"));

            assertThrows(DataIntegrityViolationException.class,
                    () -> recorder.record(subject, organizationId, conversationId, "mid.1", "hello"));

            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void aMissingConversationIsNotFound() {
            when(messages.findByOrganizationIdAndExternalMessageId(any(), any())).thenReturn(Optional.empty());
            when(conversations.findByIdAndOrganizationId(conversationId, organizationId))
                    .thenReturn(Optional.empty());

            assertThrows(ConversationNotFoundException.class,
                    () -> recorder.record(subject, organizationId, conversationId, "mid.1", "hello"));
        }
    }

    @Nested
    class AttributeStored {

        @Test
        void attributesTheStoredRowAndPublishesBecauseTheSenderChanged() {
            Message echoed = echoed();
            when(messages.findByOrganizationIdAndExternalMessageId(organizationId, "mid.1"))
                    .thenReturn(Optional.of(echoed));

            Optional<MessageResponse> result = recorder.attributeStored(subject, organizationId, "mid.1");

            assertTrue(result.isPresent());
            assertEquals(agent.getId(), result.get().senderId());
            verify(publisher).publishEvent(any(MessageReceivedEvent.class));
        }

        @Test
        void isEmptyWhenNothingIsStored() {
            when(messages.findByOrganizationIdAndExternalMessageId(organizationId, "mid.1"))
                    .thenReturn(Optional.empty());

            assertTrue(recorder.attributeStored(subject, organizationId, "mid.1").isEmpty());
            verify(publisher, never()).publishEvent(any(Object.class));
        }
    }
}
