package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.dto.MessageResponse;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.meta.service.ChannelClient;
import com.connecta.backend.meta.service.ChannelClientFactory;
import com.connecta.backend.meta.service.ChannelNotConnectedException;
import com.connecta.backend.meta.service.ChannelSendFailedException;
import com.connecta.backend.meta.service.MessagingWindowClosedException;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class OutgoingMessageServiceTests {

    @Mock
    ConversationDetailService conversations;

    @Mock
    ChannelClientFactory channels;

    @Mock
    ChannelClient client;

    @Mock
    MessageRepository messages;

    @Mock
    ActiveCaller activeCaller;

    @Mock
    ApplicationEventPublisher publisher;

    OutgoingMessageService service;

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
        service = new OutgoingMessageService(conversations, channels, messages, activeCaller, publisher,
                TestClock.FIXED);
    }

    private void conversationFound() {
        when(conversations.find(subject, organizationId, conversationId)).thenReturn(thread);
        when(activeCaller.load(subject)).thenReturn(agent);
        when(channels.forChannel(Channel.MESSENGER)).thenReturn(client);
    }

    @Nested
    class Send {

        @Test
        void sendsThenStoresAnOutboundMessageWithMetasId() {
            conversationFound();
            when(client.send(organizationId, "psid-1", "hello")).thenReturn("mid.1");
            when(messages.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));

            MessageResponse sent = service.send(subject, organizationId, conversationId, "hello");

            ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
            verify(messages).save(saved.capture());
            assertEquals("mid.1", saved.getValue().getExternalMessageId());
            assertEquals(MessageDirection.OUTBOUND, saved.getValue().getDirection());
            assertEquals(agent, saved.getValue().getSentByUser());
            assertEquals("hello", sent.text());
            assertEquals(agent.getId(), sent.senderId());
            assertEquals(TestClock.NOW, sent.sentAt());
        }

        @Test
        void movesTheConversationsLastMessageTime() {
            conversationFound();
            when(client.send(any(), any(), any())).thenReturn("mid.1");
            when(messages.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));

            service.send(subject, organizationId, conversationId, "hello");

            assertEquals(TestClock.NOW, thread.getLastMessageAt());
        }

        @Test
        void publishesAnOutboundEventForLiveUpdates() {
            conversationFound();
            when(client.send(any(), any(), any())).thenReturn("mid.1");
            when(messages.save(any(Message.class))).thenAnswer(call -> {
                Message message = call.getArgument(0);
                TestUsers.set(message, "id", UUID.randomUUID());
                return message;
            });

            service.send(subject, organizationId, conversationId, "hello");

            ArgumentCaptor<MessageReceivedEvent> event = ArgumentCaptor.forClass(MessageReceivedEvent.class);
            verify(publisher).publishEvent(event.capture());
            assertEquals(MessageDirection.OUTBOUND, event.getValue().direction());
            assertEquals(Channel.MESSENGER, event.getValue().channel());
            assertEquals(false, event.getValue().newConversation());
            assertEquals(conversationId, event.getValue().conversationId());
        }
    }

    @Nested
    class EchoFirst {

        @Test
        void returnsTheEchoedMessageAttributedToTheAgentWithoutSavingAgain() {
            conversationFound();
            when(client.send(organizationId, "psid-1", "hello")).thenReturn("mid.1");
            Message echoed = Message.outbound(thread, null, "mid.1", "hello", TestClock.NOW);
            TestUsers.set(echoed, "id", UUID.randomUUID());
            when(messages.findByOrganizationIdAndExternalMessageId(organizationId, "mid.1"))
                    .thenReturn(java.util.Optional.of(echoed));

            MessageResponse sent = service.send(subject, organizationId, conversationId, "hello");

            verify(messages, never()).save(any());
            verify(publisher, never()).publishEvent(any(Object.class));
            assertEquals(echoed.getId(), sent.id());
            assertEquals(agent.getId(), sent.senderId());
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
            verify(messages, never()).save(any());
        }

        @Test
        void anotherTenantsConversationIsNotFound() {
            when(conversations.find(subject, organizationId, conversationId))
                    .thenThrow(new ConversationNotFoundException());

            assertThrows(ConversationNotFoundException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(messages, never()).save(any());
        }

        @Test
        void aChannelWithoutAConnectedAccountStoresNothing() {
            conversationFound();
            when(client.send(any(), any(), any())).thenThrow(new ChannelNotConnectedException());

            assertThrows(ChannelNotConnectedException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(messages, never()).save(any());
            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void aMetaFailureStoresNothingAndKeepsTheLastMessageTime() {
            conversationFound();
            when(client.send(any(), any(), any())).thenThrow(new ChannelSendFailedException("boom"));

            assertThrows(ChannelSendFailedException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(messages, never()).save(any());
            verify(publisher, never()).publishEvent(any(Object.class));
            assertNull(thread.getLastMessageAt());
        }

        @Test
        void aClosedReplyWindowStoresNothing() {
            conversationFound();
            when(client.send(any(), any(), any())).thenThrow(new MessagingWindowClosedException());

            assertThrows(MessagingWindowClosedException.class,
                    () -> service.send(subject, organizationId, conversationId, "hi"));

            verify(messages, never()).save(any());
        }
    }
}
