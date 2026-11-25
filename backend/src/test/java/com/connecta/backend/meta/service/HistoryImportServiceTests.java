package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.config.MetaProperties;
import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.service.HistoryImportedEvent;
import com.connecta.backend.inbox.service.IncomingMessageService;
import com.connecta.backend.inbox.service.ParticipantNameService;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import com.connecta.backend.meta.domain.HistoryImportFailure;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

class HistoryImportServiceTests {

    private static final String KEY = "dGVzdC1vbmx5LW1ldGEtdG9rZW4ta2V5LTAwMDAwMDE=";

    private final UUID organizationId = UUID.randomUUID();
    private final UUID pageRowId = UUID.randomUUID();
    private HistoryAccountStore store;
    private ConversationHistoryClient client;
    private IncomingMessageService inbox;
    private ParticipantNameService names;
    private ApplicationEventPublisher publisher;
    private TokenCipher cipher;
    private MetaConnectedPage page;
    private HistorySource source;

    @BeforeEach
    void setUp() {
        store = mock(HistoryAccountStore.class);
        client = mock(ConversationHistoryClient.class);
        inbox = mock(IncomingMessageService.class);
        names = mock(ParticipantNameService.class);
        publisher = mock(ApplicationEventPublisher.class);
        cipher = new TokenCipher(new MetaProperties("a", "s", "v27.0", "c", "https://x", KEY));
        Organization acme = new Organization("Acme", "acme");
        TestUsers.set(acme, "id", organizationId);
        page = new MetaConnectedPage(acme, "page-1");
        source = new HistorySource(page, pageRowId, Channel.MESSENGER, organizationId, "page-1",
                cipher.encrypt("page-token"));
        when(store.find(Channel.MESSENGER, pageRowId)).thenReturn(Optional.of(source));
        when(client.channel()).thenReturn(Channel.MESSENGER);
    }

    private HistoryImportService service(HistoryImportProperties limits) {
        return new HistoryImportService(store, new ConversationHistoryClientFactory(List.of(client)), cipher, inbox,
                names, publisher, limits, TestClock.FIXED);
    }

    private static HistoryImportProperties limits(int conversations, int messages) {
        return new HistoryImportProperties(true, conversations, 50, messages, 25, Duration.ofSeconds(5), 1, 10, 200);
    }

    private static NormalizedMessage message(String id, String peer) {
        return new NormalizedMessage(WebhookObject.PAGE, id, peer, Direction.INBOUND, "hi " + id,
                Instant.parse("2026-11-20T10:00:00Z"));
    }

    private static HistoryConversation conversation(String peer, String name, boolean more, String... ids) {
        List<NormalizedMessage> messages = new ArrayList<>();
        for (String id : ids) {
            messages.add(message(id, peer));
        }
        return new HistoryConversation(peer, name, messages, more);
    }

    private void history(HistoryConversation... conversations) {
        when(client.conversations("page-1", "page-token")).thenReturn(List.of(conversations).iterator());
    }

    @Nested
    class Importing {

        @Test
        void storesEveryMessageOldestFirstThroughTheInboxAndCountsThem() {
            history(conversation("c1", "Rina", false, "m1", "m2"), conversation("c2", "Karim", false, "m3"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            HistoryImportResult result = service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId)
                    .orElseThrow();

            assertEquals(new HistoryImportResult(2, 3, 0, false), result);
            InOrder order = inOrder(inbox);
            order.verify(inbox).importHistorical(eq(page), eq(message("m1", "c1")));
            order.verify(inbox).importHistorical(eq(page), eq(message("m2", "c1")));
            order.verify(inbox).importHistorical(eq(page), eq(message("m3", "c2")));
        }

        @Test
        void usesTheDecryptedTokenAndNeverTheStoredCiphertext() {
            history();
            service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId);

            verify(client).conversations("page-1", "page-token");
        }

        @Test
        void aMessageTheOrganizationAlreadyHasIsSkipped() {
            history(conversation("c1", "Rina", false, "m1", "m2"));
            when(inbox.importHistorical(any(), eq(message("m1", "c1")))).thenReturn(false);
            when(inbox.importHistorical(any(), eq(message("m2", "c1")))).thenReturn(true);

            HistoryImportResult result = service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId)
                    .orElseThrow();

            assertEquals(1, result.messagesImported());
            assertEquals(1, result.messagesSkipped());
        }

        @Test
        void aRepeatRunStoresNothingNewButStillAnnouncesSoPicturesGetRefreshed() {
            history(conversation("c1", "Rina", false, "m1"));
            when(inbox.importHistorical(any(), any())).thenReturn(false);

            HistoryImportResult result = service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId)
                    .orElseThrow();

            assertEquals(new HistoryImportResult(1, 0, 1, false), result);
            verify(publisher).publishEvent(new HistoryImportedEvent(organizationId));
        }

        @Test
        void fillsTheCustomerNameThroughTheNameService() {
            history(conversation("c1", "Rina", false, "m1"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId);

            verify(names).nameIfAbsent(organizationId, Channel.MESSENGER, "c1", "Rina");
        }

        @Test
        void anUnknownAccountImportsNothing() {
            assertTrue(service(limits(50, 1000)).importHistory(Channel.MESSENGER, UUID.randomUUID()).isEmpty());
            verify(inbox, never()).importHistorical(any(), any());
        }
    }

    @Nested
    class Limits {

        @Test
        void stopsAtTheConversationCapAndReportsTruncated() {
            history(conversation("c1", null, false, "m1"), conversation("c2", null, false, "m2"),
                    conversation("c3", null, false, "m3"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            HistoryImportResult result = service(limits(2, 1000)).importHistory(Channel.MESSENGER, pageRowId)
                    .orElseThrow();

            assertEquals(2, result.conversations());
            assertTrue(result.truncated());
            verify(inbox, never()).importHistorical(any(), eq(message("m3", "c3")));
        }

        @Test
        void stopsAtTheTotalMessageCapMidConversation() {
            history(conversation("c1", null, false, "m1", "m2", "m3"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            HistoryImportResult result = service(limits(50, 2)).importHistory(Channel.MESSENGER, pageRowId)
                    .orElseThrow();

            assertEquals(2, result.messagesImported());
            assertTrue(result.truncated());
        }

        @Test
        void moreMessagesThanThePerConversationLimitIsTruncated() {
            history(conversation("c1", null, true, "m1"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            assertTrue(service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId)
                    .orElseThrow().truncated());
        }

        @Test
        void aTruncatedImportIsStillMarkedImported() {
            history(conversation("c1", null, true, "m1"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId);

            verify(store).markImported(Channel.MESSENGER, pageRowId, TestClock.FIXED.instant());
        }
    }

    @Nested
    class Announcing {

        @Test
        void publishesOneSummaryEventNotOnePerMessage() {
            history(conversation("c1", null, false, "m1", "m2", "m3"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId);

            verify(publisher, times(1)).publishEvent(new HistoryImportedEvent(organizationId));
        }

        @Test
        void marksTheAccountImportedAfterASuccessfulRun() {
            history(conversation("c1", null, false, "m1"));
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId);

            verify(store).markImported(Channel.MESSENGER, pageRowId, TestClock.FIXED.instant());
        }
    }

    @Nested
    class Failing {

        @Test
        void aMetaFailureMidWayKeepsWhatWasStoredAndLeavesTheAccountUnmarked() {
            Iterator<HistoryConversation> failing = new Iterator<>() {
                private int calls;

                @Override
                public boolean hasNext() {
                    if (calls == 1) {
                        throw new HistoryFetchException("boom");
                    }
                    return true;
                }

                @Override
                public HistoryConversation next() {
                    calls++;
                    return conversation("c1", null, false, "m1");
                }
            };
            when(client.conversations("page-1", "page-token")).thenReturn(failing);
            when(inbox.importHistorical(any(), any())).thenReturn(true);

            assertThrows(HistoryFetchException.class,
                    () -> service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId));

            verify(inbox).importHistorical(any(), eq(message("m1", "c1")));
            verify(store, never()).markImported(any(), any(), any());
            verify(publisher).publishEvent(new HistoryImportedEvent(organizationId));
        }

        @Test
        void aPermissionFailureIsRecordedAsPermission() {
            when(client.conversations("page-1", "page-token")).thenThrow(new HistoryPermissionException());

            assertThrows(HistoryPermissionException.class,
                    () -> service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId));

            verify(store).markFailed(Channel.MESSENGER, pageRowId, HistoryImportFailure.PERMISSION,
                    TestClock.FIXED.instant());
        }

        @Test
        void aFetchFailureIsRecordedAsFetch() {
            when(client.conversations("page-1", "page-token")).thenThrow(new HistoryFetchException("x"));

            assertThrows(HistoryFetchException.class,
                    () -> service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId));

            verify(store).markFailed(Channel.MESSENGER, pageRowId, HistoryImportFailure.FETCH,
                    TestClock.FIXED.instant());
        }

        @Test
        void aSuccessfulRunRecordsNoFailure() {
            history();

            service(limits(50, 1000)).importHistory(Channel.MESSENGER, pageRowId);

            verify(store, never()).markFailed(any(), any(), any(), any());
        }

        @Test
        void aSecondImportOfTheSameAccountWhileOneRunsIsRefused() {
            HistoryImportService service = service(limits(50, 1000));
            when(client.conversations("page-1", "page-token")).thenAnswer(call -> {
                assertThrows(HistoryImportInProgressException.class,
                        () -> service.importHistory(Channel.MESSENGER, pageRowId));
                return List.<HistoryConversation>of().iterator();
            });

            service.importHistory(Channel.MESSENGER, pageRowId);

            assertFalse(service.importHistory(Channel.MESSENGER, pageRowId).isEmpty());
        }

        @Test
        void theGuardIsReleasedAfterAFailure() {
            when(client.conversations("page-1", "page-token")).thenThrow(new HistoryFetchException("x"));
            HistoryImportService service = service(limits(50, 1000));

            assertThrows(HistoryFetchException.class, () -> service.importHistory(Channel.MESSENGER, pageRowId));
            assertThrows(HistoryFetchException.class, () -> service.importHistory(Channel.MESSENGER, pageRowId));
        }
    }
}
