package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.MetaSyncProperties;
import com.connecta.backend.inbox.domain.Channel;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MetaSyncJobTests {

    private static final MetaSyncProperties ON = MetaSyncProperties.defaults();
    private static final MetaSyncProperties OFF = new MetaSyncProperties(false, null, 0, 0, null, null);
    private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");
    /** What an account that never synced is read with: a first window back. */
    private static final HistoryBounds FIRST_RUN = ON.bounds(ON.since(null, NOW));

    private final HistoryImportService imports = mock(HistoryImportService.class);
    private final HistoryAccountStore store = mock(HistoryAccountStore.class);
    private final PendingHistoryImport page = new PendingHistoryImport(Channel.MESSENGER, UUID.randomUUID());
    private final PendingHistoryImport instagram = new PendingHistoryImport(Channel.INSTAGRAM, UUID.randomUUID());
    private final PendingHistoryImport otherPage = new PendingHistoryImport(Channel.MESSENGER, UUID.randomUUID());

    private MetaSyncJob job(MetaSyncProperties properties, Executor executor) {
        return new MetaSyncJob(imports, store, properties, executor, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Optional<HistoryImportResult> stored(int inserted) {
        return Optional.of(new HistoryImportResult(1, inserted, 0, 0, false));
    }

    @Nested
    class Visiting {

        @Test
        void everyConnectedAccountIsSyncedWithTheSmallBounds() {
            when(store.connected()).thenReturn(List.of(page, instagram));
            when(imports.syncRecent(any(), any(), any())).thenReturn(stored(1));

            job(ON, Runnable::run).sync();

            verify(imports).syncRecent(Channel.MESSENGER, page.accountId(), FIRST_RUN);
            verify(imports).syncRecent(Channel.INSTAGRAM, instagram.accountId(), FIRST_RUN);
        }

        @Test
        void anAccountSyncedBeforeIsReadFromItsLastSyncMinusTheOverlap() {
            Instant lastSyncedAt = NOW.minus(Duration.ofHours(3));
            when(store.connected()).thenReturn(List.of(page, instagram));
            when(store.lastSyncedAt(Channel.MESSENGER, page.accountId())).thenReturn(Optional.of(lastSyncedAt));
            when(imports.syncRecent(any(), any(), any())).thenReturn(stored(1));

            job(ON, Runnable::run).sync();

            verify(imports).syncRecent(Channel.MESSENGER, page.accountId(),
                    ON.bounds(lastSyncedAt.minus(Duration.ofMinutes(15))));
            verify(imports).syncRecent(Channel.INSTAGRAM, instagram.accountId(), FIRST_RUN);
        }

        @Test
        void oneFailingAccountNeverStopsTheOthers() {
            when(store.connected()).thenReturn(List.of(page, instagram, otherPage));
            when(imports.syncRecent(Channel.MESSENGER, page.accountId(), FIRST_RUN))
                    .thenThrow(new HistoryFetchException("down"));
            when(imports.syncRecent(Channel.INSTAGRAM, instagram.accountId(), FIRST_RUN))
                    .thenThrow(new IllegalStateException("token"));
            when(imports.syncRecent(Channel.MESSENGER, otherPage.accountId(), FIRST_RUN)).thenReturn(stored(2));

            assertDoesNotThrow(() -> job(ON, Runnable::run).sync());

            verify(imports).syncRecent(Channel.MESSENGER, otherPage.accountId(), FIRST_RUN);
        }

        @Test
        void anAccountBeingImportedIsSkippedAndTheRestContinue() {
            when(store.connected()).thenReturn(List.of(page, instagram));
            when(imports.syncRecent(Channel.MESSENGER, page.accountId(), FIRST_RUN))
                    .thenThrow(new HistoryImportInProgressException());
            when(imports.syncRecent(Channel.INSTAGRAM, instagram.accountId(), FIRST_RUN)).thenReturn(stored(0));

            assertDoesNotThrow(() -> job(ON, Runnable::run).sync());

            verify(imports).syncRecent(Channel.INSTAGRAM, instagram.accountId(), FIRST_RUN);
        }

        @Test
        void aStoreFailureIsContained() {
            when(store.connected()).thenThrow(new IllegalStateException("db"));

            assertDoesNotThrow(() -> job(ON, Runnable::run).sync());

            verifyNoInteractions(imports);
        }
    }

    @Nested
    class Scheduling {

        @Test
        void aDisabledSyncDoesNothing() {
            job(OFF, Runnable::run).sync();
            job(OFF, Runnable::run).onStartup();

            verifyNoInteractions(store, imports);
        }

        @Test
        void startupRunsTheSyncToo() {
            when(store.connected()).thenReturn(List.of(page));
            when(imports.syncRecent(any(), any(), any())).thenReturn(stored(0));

            job(ON, Runnable::run).onStartup();

            verify(imports).syncRecent(Channel.MESSENGER, page.accountId(), FIRST_RUN);
        }

        @Test
        void theBatchRunsOnTheExecutorNotOnTheCallingThread() {
            Runnable[] queued = new Runnable[1];
            when(store.connected()).thenReturn(List.of());

            job(ON, task -> queued[0] = task).sync();

            verify(store, never()).connected();
            queued[0].run();
            verify(store).connected();
        }

        @Test
        void aBusyExecutorDropsTheRunWithoutFailing() {
            Executor busy = task -> {
                throw new RejectedExecutionException("busy");
            };

            assertDoesNotThrow(() -> job(ON, busy).sync());

            verifyNoInteractions(store, imports);
        }
    }
}
