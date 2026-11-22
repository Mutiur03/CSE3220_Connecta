package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.meta.domain.MetaConnectedAccount;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.domain.MetaProcessedMessage;
import com.connecta.backend.meta.domain.MetaWebhookEvent;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaConnectedAccountRepository;
import com.connecta.backend.meta.repository.MetaConnectedPageRepository;
import com.connecta.backend.meta.repository.MetaProcessedMessageRepository;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

class WebhookEventProcessorTests {

    private static final String PAGE_PAYLOAD = "{\"object\":\"page\",\"entry\":[{\"id\":\"page-1\",\"messaging\":["
            + "{\"sender\":{\"id\":\"customer-1\"},\"timestamp\":1700000000000,\"message\":{\"mid\":\"m.1\",\"text\":\"Hi\"}}]}]}";
    private static final java.time.Instant SENT_AT = java.time.Instant.ofEpochMilli(1700000000000L);

    private MetaConnectedPageRepository pages;
    private MetaConnectedAccountRepository accounts;
    private MetaProcessedMessageRepository processed;
    private ApplicationEventPublisher publisher;
    private WebhookEventProcessor processor;

    private Organization acme;
    private Organization other;

    @BeforeEach
    void setUp() {
        pages = mock(MetaConnectedPageRepository.class);
        accounts = mock(MetaConnectedAccountRepository.class);
        processed = mock(MetaProcessedMessageRepository.class);
        publisher = mock(ApplicationEventPublisher.class);
        processor = new WebhookEventProcessor(pages, accounts, processed, new MessagingEventParser(), publisher,
                new ObjectMapper(), TestClock.FIXED);
        acme = organization();
        other = organization();
    }

    private static Organization organization() {
        Organization org = new Organization("Org " + UUID.randomUUID(), "org-" + UUID.randomUUID());
        TestUsers.set(org, "id", UUID.randomUUID());
        return org;
    }

    private MetaConnectedPage pageOf(Organization org, String pageId) {
        return new MetaConnectedPage(org, pageId);
    }

    private static MetaWebhookEvent event(WebhookObject object, String payload) {
        return new MetaWebhookEvent(object, payload, TestClock.NOW);
    }

    @Nested
    class Routing {

        @Test
        void aMessageIsDeliveredToTheOrganizationThatConnectedThePage() {
            when(pages.findAllByPageId("page-1")).thenReturn(List.of(pageOf(acme, "page-1")));
            when(processed.existsByOrganizationIdAndObjectAndExternalMessageId(any(), any(), anyString()))
                    .thenReturn(false);

            processor.process(event(WebhookObject.PAGE, PAGE_PAYLOAD));

            ArgumentCaptor<MessageReceivedEvent> captor = ArgumentCaptor.forClass(MessageReceivedEvent.class);
            verify(publisher).publishEvent(captor.capture());
            assertEquals(acme.getId(), captor.getValue().organizationId());
            assertEquals("m.1", captor.getValue().message().externalMessageId());
        }

        @Test
        void aPageConnectedByTwoOrganizationsIsDeliveredToBoth() {
            when(pages.findAllByPageId("page-1")).thenReturn(List.of(pageOf(acme, "page-1"), pageOf(other, "page-1")));
            when(processed.existsByOrganizationIdAndObjectAndExternalMessageId(any(), any(), anyString()))
                    .thenReturn(false);

            processor.process(event(WebhookObject.PAGE, PAGE_PAYLOAD));

            verify(publisher).publishEvent(new MessageReceivedEvent(acme.getId(),
                    new NormalizedMessage(WebhookObject.PAGE, "m.1", "customer-1",
                            NormalizedMessage.Direction.INBOUND, "Hi", SENT_AT)));
            verify(publisher).publishEvent(new MessageReceivedEvent(other.getId(),
                    new NormalizedMessage(WebhookObject.PAGE, "m.1", "customer-1",
                            NormalizedMessage.Direction.INBOUND, "Hi", SENT_AT)));
        }

        @Test
        void aPageNoOrganizationConnectedIsIgnored() {
            when(pages.findAllByPageId("page-1")).thenReturn(List.of());

            processor.process(event(WebhookObject.PAGE, PAGE_PAYLOAD));

            verify(publisher, never()).publishEvent(any());
            verify(processed, never()).save(any());
        }

        @Test
        void anInstagramEventRoutesByTheAccountNotThePage() {
            MetaConnectedAccount account = new MetaConnectedAccount(acme, MetaConnectedAccount.PLATFORM_INSTAGRAM,
                    "ig-1");
            when(accounts.findAllByPlatformAndExternalAccountId("INSTAGRAM", "ig-1")).thenReturn(List.of(account));
            when(processed.existsByOrganizationIdAndObjectAndExternalMessageId(any(), any(), anyString()))
                    .thenReturn(false);
            String payload = "{\"object\":\"instagram\",\"entry\":[{\"id\":\"ig-1\",\"messaging\":["
                    + "{\"sender\":{\"id\":\"ig-customer\"},\"message\":{\"mid\":\"m.9\",\"text\":\"DM\"}}]}]}";

            processor.process(event(WebhookObject.INSTAGRAM, payload));

            verify(publisher).publishEvent(any(MessageReceivedEvent.class));
        }

        @Test
        void theEventIsMarkedProcessedEvenWhenNothingWasRouted() {
            MetaWebhookEvent stored = event(WebhookObject.PAGE, PAGE_PAYLOAD);
            when(pages.findAllByPageId("page-1")).thenReturn(List.of());

            processor.process(stored);

            assertNotNull(stored.getProcessedAt());
        }
    }

    @Nested
    class Deduplication {

        @Test
        void aRedeliveredMessageIsNotDeliveredAgain() {
            when(pages.findAllByPageId("page-1")).thenReturn(List.of(pageOf(acme, "page-1")));
            when(processed.existsByOrganizationIdAndObjectAndExternalMessageId(acme.getId(), WebhookObject.PAGE,
                    "m.1")).thenReturn(true);

            processor.process(event(WebhookObject.PAGE, PAGE_PAYLOAD));

            verify(publisher, never()).publishEvent(any());
            verify(processed, never()).save(any());
        }

        @Test
        void aNewMessageIsRecordedBeforeItIsPublished() {
            when(pages.findAllByPageId("page-1")).thenReturn(List.of(pageOf(acme, "page-1")));
            when(processed.existsByOrganizationIdAndObjectAndExternalMessageId(any(), any(), anyString()))
                    .thenReturn(false);

            processor.process(event(WebhookObject.PAGE, PAGE_PAYLOAD));

            ArgumentCaptor<MetaProcessedMessage> captor = ArgumentCaptor.forClass(MetaProcessedMessage.class);
            verify(processed).save(captor.capture());
            assertEquals(acme, captor.getValue().getOrganization());
            assertEquals("m.1", captor.getValue().getExternalMessageId());
            assertTrue(captor.getValue().getProcessedAt() != null);
        }

        @Test
        void theSameMidInAnotherOrganizationIsStillDelivered() {
            when(pages.findAllByPageId("page-1")).thenReturn(List.of(pageOf(acme, "page-1"), pageOf(other, "page-1")));
            when(processed.existsByOrganizationIdAndObjectAndExternalMessageId(acme.getId(), WebhookObject.PAGE, "m.1"))
                    .thenReturn(true);
            when(processed.existsByOrganizationIdAndObjectAndExternalMessageId(eq(other.getId()), eq(WebhookObject.PAGE),
                    eq("m.1"))).thenReturn(false);

            processor.process(event(WebhookObject.PAGE, PAGE_PAYLOAD));

            verify(publisher, never()).publishEvent(eq(new MessageReceivedEvent(acme.getId(),
                    new NormalizedMessage(WebhookObject.PAGE, "m.1", "customer-1",
                            NormalizedMessage.Direction.INBOUND, "Hi", SENT_AT))));
            verify(publisher).publishEvent(any(MessageReceivedEvent.class));
        }
    }
}
