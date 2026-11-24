package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.inbox.domain.ConversationParticipant;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageDirection;
import com.connecta.backend.inbox.domain.Participant;
import com.connecta.backend.inbox.repository.ConversationParticipantRepository;
import com.connecta.backend.inbox.repository.ConversationRepository;
import com.connecta.backend.inbox.repository.MessageRepository;
import com.connecta.backend.inbox.repository.ParticipantRepository;
import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class IncomingMessageServiceTests {

    private static final Instant SENT_AT = TestClock.NOW;

    private ParticipantRepository participants;
    private ConversationRepository conversations;
    private ConversationParticipantRepository links;
    private MessageRepository messages;
    private ApplicationEventPublisher publisher;
    private IncomingMessageService service;

    private Organization acme;
    private Organization other;
    private MetaConnectedPage acmePage;
    private MetaConnectedPage otherPage;
    private MetaConnectedAccount acmeInstagram;

    @BeforeEach
    void setUp() {
        participants = mock(ParticipantRepository.class);
        conversations = mock(ConversationRepository.class);
        links = mock(ConversationParticipantRepository.class);
        messages = mock(MessageRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        service = new IncomingMessageService(participants, conversations, links, messages, publisher);
        acme = organization();
        other = organization();
        acmePage = new MetaConnectedPage(acme, "page-1");
        otherPage = new MetaConnectedPage(other, "page-1");
        acmeInstagram = new MetaConnectedAccount(acme, MetaConnectedAccount.PLATFORM_INSTAGRAM, "ig-1");
        when(participants.save(any(Participant.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(conversations.save(any(Conversation.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(messages.save(any(Message.class))).thenAnswer(call -> withId(call.getArgument(0)));
        when(participants.findByOrganizationIdAndChannelAndExternalId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(conversations.findByOrganizationIdAndChannelAndExternalThreadId(any(), any(), any()))
                .thenReturn(Optional.empty());
    }

    private static Organization organization() {
        Organization org = new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID());
        TestUsers.set(org, "id", UUID.randomUUID());
        return org;
    }

    private static <T> T withId(T entity) {
        TestUsers.set(entity, "id", UUID.randomUUID());
        return entity;
    }

    private static NormalizedMessage message(WebhookObject object, Direction direction, String mid) {
        return new NormalizedMessage(object, mid, "customer-1", direction, "Hi", SENT_AT);
    }

    private MessageReceivedEvent publishedEvent() {
        ArgumentCaptor<MessageReceivedEvent> captor = ArgumentCaptor.forClass(MessageReceivedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Nested
    class FirstMessageFromACustomer {

        @Test
        void createsTheCustomerTheConversationAndTheLinkBetweenThem() {
            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.1"));

            ArgumentCaptor<Participant> customer = ArgumentCaptor.forClass(Participant.class);
            verify(participants).save(customer.capture());
            assertEquals(acme, customer.getValue().getOrganization());
            assertEquals(Channel.MESSENGER, customer.getValue().getChannel());
            assertEquals("customer-1", customer.getValue().getExternalId());
            ArgumentCaptor<Conversation> conversation = ArgumentCaptor.forClass(Conversation.class);
            verify(conversations).save(conversation.capture());
            assertEquals("customer-1", conversation.getValue().getExternalThreadId());
            assertEquals(SENT_AT, conversation.getValue().getLastMessageAt());
            verify(links).save(any(ConversationParticipant.class));
        }

        @Test
        void savesAnInboundMessageFromTheCustomer() {
            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.1"));

            ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
            verify(messages).save(saved.capture());
            assertEquals(MessageDirection.INBOUND, saved.getValue().getDirection());
            assertEquals("m.1", saved.getValue().getExternalMessageId());
            assertEquals("Hi", saved.getValue().getBody());
            assertEquals(SENT_AT, saved.getValue().getSentAt());
            assertEquals(acme, saved.getValue().getOrganization());
            assertTrue(saved.getValue().getSenderParticipant() != null);
        }

        @Test
        void publishesAnEventMarkingTheConversationAsNew() {
            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.1"));

            MessageReceivedEvent event = publishedEvent();
            assertEquals(acme.getId(), event.organizationId());
            assertEquals(Channel.MESSENGER, event.channel());
            assertEquals(MessageDirection.INBOUND, event.direction());
            assertTrue(event.newConversation());
            assertTrue(event.conversationId() != null && event.messageId() != null);
        }

        @Test
        void anInstagramMessageIsOnTheInstagramChannel() {
            service.receive(acmeInstagram, message(WebhookObject.INSTAGRAM, Direction.INBOUND, "m.2"));

            assertEquals(Channel.INSTAGRAM, publishedEvent().channel());
            verify(conversations).findByOrganizationIdAndChannelAndExternalThreadId(acme.getId(),
                    Channel.INSTAGRAM, "customer-1");
        }
    }

    @Nested
    class FollowUpMessage {

        @Test
        void reusesTheCustomerAndConversationInTheSameOrganization() {
            Participant customer = withId(new Participant(acme, Channel.MESSENGER, "customer-1", null));
            Conversation thread = withId(new Conversation(acme, Channel.MESSENGER, "customer-1"));
            when(participants.findByOrganizationIdAndChannelAndExternalId(acme.getId(), Channel.MESSENGER,
                    "customer-1")).thenReturn(Optional.of(customer));
            when(conversations.findByOrganizationIdAndChannelAndExternalThreadId(acme.getId(), Channel.MESSENGER,
                    "customer-1")).thenReturn(Optional.of(thread));

            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.3"));

            verify(participants, never()).save(any());
            verify(conversations, never()).save(any());
            verify(links, never()).save(any());
            ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
            verify(messages).save(saved.capture());
            assertSame(thread, saved.getValue().getConversation());
            assertSame(customer, saved.getValue().getSenderParticipant());
            assertFalse(publishedEvent().newConversation());
        }

        @Test
        void anOlderMessageDoesNotRewindTheLastMessageTime() {
            Conversation thread = withId(new Conversation(acme, Channel.MESSENGER, "customer-1"));
            thread.recordMessageAt(SENT_AT.plusSeconds(60));
            when(conversations.findByOrganizationIdAndChannelAndExternalThreadId(any(), any(), any()))
                    .thenReturn(Optional.of(thread));

            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.4"));

            assertEquals(SENT_AT.plusSeconds(60), thread.getLastMessageAt());
        }
    }

    @Nested
    class OutboundEcho {

        @Test
        void isStoredAsAnOutboundMessageWithoutAnAgent() {
            service.receive(acmePage, message(WebhookObject.PAGE, Direction.OUTBOUND, "m.5"));

            ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
            verify(messages).save(saved.capture());
            assertEquals(MessageDirection.OUTBOUND, saved.getValue().getDirection());
            assertNull(saved.getValue().getSentByUser());
            assertNull(saved.getValue().getSenderParticipant());
            assertEquals(MessageDirection.OUTBOUND, publishedEvent().direction());
        }
    }

    @Nested
    class DuplicateMessage {

        @Test
        void aMessageTheOrganizationAlreadyHasIsNeitherSavedNorPublished() {
            when(messages.existsByOrganizationIdAndExternalMessageId(acme.getId(), "m.1")).thenReturn(true);

            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.1"));

            verify(messages, never()).save(any());
            verify(participants, never()).save(any());
            verify(conversations, never()).save(any());
            verify(publisher, never()).publishEvent(any(Object.class));
        }
    }

    @Nested
    class TenantIsolation {

        @Test
        void theSameMessageIdInAnotherOrganizationIsStillStored() {
            when(messages.existsByOrganizationIdAndExternalMessageId(acme.getId(), "m.1")).thenReturn(true);
            when(messages.existsByOrganizationIdAndExternalMessageId(other.getId(), "m.1")).thenReturn(false);

            service.receive(otherPage, message(WebhookObject.PAGE, Direction.INBOUND, "m.1"));

            verify(messages).save(any(Message.class));
            assertEquals(other.getId(), publishedEvent().organizationId());
        }

        @Test
        void lookupsAreScopedToTheReceivingOrganization() {
            service.receive(otherPage, message(WebhookObject.PAGE, Direction.INBOUND, "m.6"));

            verify(participants).findByOrganizationIdAndChannelAndExternalId(other.getId(), Channel.MESSENGER,
                    "customer-1");
            verify(conversations).findByOrganizationIdAndChannelAndExternalThreadId(other.getId(),
                    Channel.MESSENGER, "customer-1");
            verify(participants, never()).findByOrganizationIdAndChannelAndExternalId(acme.getId(),
                    Channel.MESSENGER, "customer-1");
        }
    }

    @Nested
    class ConnectedAccount {

        @Test
        void aNewMessengerConversationRecordsThePageTheWebhookArrivedOn() {
            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.1"));

            ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
            verify(conversations).save(saved.capture());
            assertSame(acmePage, saved.getValue().getMetaPage());
            assertNull(saved.getValue().getInstagramAccount());
        }

        @Test
        void aNewInstagramConversationRecordsTheAccountTheWebhookArrivedOn() {
            service.receive(acmeInstagram, message(WebhookObject.INSTAGRAM, Direction.INBOUND, "m.2"));

            ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
            verify(conversations).save(saved.capture());
            assertSame(acmeInstagram, saved.getValue().getInstagramAccount());
            assertNull(saved.getValue().getMetaPage());
        }

        @Test
        void aFollowUpMessageNeverChangesTheRecordedAccount() {
            MetaConnectedPage firstPage = new MetaConnectedPage(acme, "page-0");
            Conversation thread = withId(new Conversation(acme, Channel.MESSENGER, "customer-1"));
            thread.linkToPage(firstPage);
            when(conversations.findByOrganizationIdAndChannelAndExternalThreadId(any(), any(), any()))
                    .thenReturn(Optional.of(thread));

            service.receive(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "m.3"));

            assertSame(firstPage, thread.getMetaPage());
        }

        @Test
        void theConversationOfAnotherOrganizationIsNotLinkedToThisOnesAccount() {
            service.receive(otherPage, message(WebhookObject.PAGE, Direction.INBOUND, "m.4"));

            ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
            verify(conversations).save(saved.capture());
            assertEquals(other, saved.getValue().getOrganization());
            assertSame(otherPage, saved.getValue().getMetaPage());
        }
    }

    @Nested
    class ImportHistorical {

        @Test
        void storesTheMessageLikeAWebhookOneButAnnouncesNothing() {
            boolean stored = service.importHistorical(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "h.1"));

            assertTrue(stored);
            ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
            verify(messages).save(saved.capture());
            assertEquals(SENT_AT, saved.getValue().getSentAt());
            ArgumentCaptor<Conversation> conversation = ArgumentCaptor.forClass(Conversation.class);
            verify(conversations).save(conversation.capture());
            assertSame(acmePage, conversation.getValue().getMetaPage());
            assertEquals(SENT_AT, conversation.getValue().getLastMessageAt());
            verify(publisher, never()).publishEvent(any(Object.class));
        }

        @Test
        void aMessageTheOrganizationAlreadyHasReturnsFalseAndStoresNothing() {
            when(messages.existsByOrganizationIdAndExternalMessageId(acme.getId(), "h.2")).thenReturn(true);

            boolean stored = service.importHistorical(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "h.2"));

            assertFalse(stored);
            verify(messages, never()).save(any(Message.class));
        }

        @Test
        void aMessageAnotherOrganizationHasIsStillStoredForThisOne() {
            when(messages.existsByOrganizationIdAndExternalMessageId(other.getId(), "h.3")).thenReturn(true);

            assertTrue(service.importHistorical(acmePage, message(WebhookObject.PAGE, Direction.INBOUND, "h.3")));
        }
    }
}
