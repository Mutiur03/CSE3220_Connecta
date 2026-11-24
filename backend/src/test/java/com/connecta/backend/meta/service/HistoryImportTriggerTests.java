package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.HistoryImportProperties;
import com.connecta.backend.inbox.domain.Channel;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class HistoryImportTriggerTests {

    private static final HistoryImportProperties ON = HistoryImportProperties.defaults();
    private static final HistoryImportProperties OFF = new HistoryImportProperties(false, 0, 0, 0, 0,
            Duration.ofSeconds(1), 1, 1, 1);

    private final HistoryImportService imports = mock(HistoryImportService.class);
    private final HistoryAccountStore store = mock(HistoryAccountStore.class);
    private final Executor inline = Runnable::run;
    private final UUID accountId = UUID.randomUUID();

    private HistoryImportTrigger trigger(HistoryImportProperties properties) {
        return new HistoryImportTrigger(new HistoryImportScheduler(imports, inline), store, properties);
    }

    @Nested
    class OnConnect {

        @Test
        void aConnectionImportsThatAccount() {
            trigger(ON).onConnected(new ChannelConnectedEvent(Channel.MESSENGER, accountId));

            verify(imports).importHistory(Channel.MESSENGER, accountId);
        }

        @Test
        void aDisabledImportDoesNothing() {
            trigger(OFF).onConnected(new ChannelConnectedEvent(Channel.MESSENGER, accountId));

            verify(imports, never()).importHistory(any(), any());
        }

        @Test
        void aFailingImportNeverReachesTheConnectingCaller() {
            when(imports.importHistory(Channel.INSTAGRAM, accountId)).thenThrow(new HistoryFetchException("down"));

            assertDoesNotThrow(() -> trigger(ON).onConnected(new ChannelConnectedEvent(Channel.INSTAGRAM, accountId)));
        }

        @Test
        void aFullQueueNeverReachesTheConnectingCaller() {
            Executor full = job -> {
                throw new RejectedExecutionException("full");
            };
            HistoryImportTrigger trigger = new HistoryImportTrigger(new HistoryImportScheduler(imports, full), store,
                    ON);

            assertDoesNotThrow(() -> trigger.onConnected(new ChannelConnectedEvent(Channel.MESSENGER, accountId)));
        }

        @Test
        void theJobRunsOnTheExecutorNotOnTheCallingThread() {
            Executor[] captured = new Executor[1];
            Runnable[] job = new Runnable[1];
            captured[0] = r -> job[0] = r;
            HistoryImportTrigger trigger = new HistoryImportTrigger(new HistoryImportScheduler(imports, captured[0]),
                    store, ON);

            trigger.onConnected(new ChannelConnectedEvent(Channel.MESSENGER, accountId));

            verify(imports, never()).importHistory(any(), any());
            job[0].run();
            verify(imports).importHistory(Channel.MESSENGER, accountId);
        }
    }

    @Nested
    class CatchUp {

        @Test
        void importsEveryPendingConnectionOneAfterAnother() {
            UUID other = UUID.randomUUID();
            when(store.pending()).thenReturn(List.of(new PendingHistoryImport(Channel.MESSENGER, accountId),
                    new PendingHistoryImport(Channel.INSTAGRAM, other)));

            trigger(ON).catchUp();

            verify(imports).importHistory(Channel.MESSENGER, accountId);
            verify(imports).importHistory(Channel.INSTAGRAM, other);
        }

        @Test
        void aFailedAccountDoesNotStopTheNext() {
            UUID other = UUID.randomUUID();
            when(store.pending()).thenReturn(List.of(new PendingHistoryImport(Channel.MESSENGER, accountId),
                    new PendingHistoryImport(Channel.INSTAGRAM, other)));
            when(imports.importHistory(Channel.MESSENGER, accountId)).thenThrow(new HistoryPermissionException());
            when(imports.importHistory(Channel.INSTAGRAM, other)).thenReturn(Optional.empty());

            trigger(ON).catchUp();

            verify(imports).importHistory(Channel.INSTAGRAM, other);
        }

        @Test
        void nothingPendingStartsNothing() {
            when(store.pending()).thenReturn(List.of());

            trigger(ON).catchUp();

            verify(imports, never()).importHistory(any(), any());
        }

        @Test
        void aDisabledImportSkipsTheCatchUp() {
            trigger(OFF).catchUp();

            verify(store, never()).pending();
        }

        @Test
        void aDatabaseFailureDoesNotStopStartup() {
            when(store.pending()).thenThrow(new IllegalStateException("db"));

            assertDoesNotThrow(() -> trigger(ON).catchUp());
        }
    }
}
