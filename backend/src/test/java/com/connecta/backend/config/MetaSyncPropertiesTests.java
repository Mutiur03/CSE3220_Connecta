package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.meta.service.HistoryBounds;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetaSyncPropertiesTests {

    private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");

    @Test
    void missingOrNonPositiveValuesFallBackToTheDefaults() {
        MetaSyncProperties p = new MetaSyncProperties(null, Duration.ZERO, -1, 0, Duration.ofSeconds(-1), null);

        assertTrue(p.enabled());
        assertEquals(Duration.ofMinutes(10), p.interval());
        assertEquals(1000, p.maxConversations());
        assertEquals(200, p.messagesPerConversation());
        assertEquals(Duration.ofMinutes(15), p.overlap());
        assertEquals(Duration.ofDays(1), p.firstWindow());
    }

    @Test
    void explicitValuesAreKept() {
        MetaSyncProperties p = new MetaSyncProperties(false, Duration.ofMinutes(1), 3, 4, Duration.ofMinutes(2),
                Duration.ofHours(3));

        assertFalse(p.enabled());
        assertEquals(Duration.ofMinutes(1), p.interval());
        assertEquals(3, p.maxConversations());
        assertEquals(Duration.ofMinutes(2), p.overlap());
        assertEquals(Duration.ofHours(3), p.firstWindow());
    }

    @Test
    void aRunStartsAShortOverlapBeforeTheLastGoodSync() {
        Instant lastSyncedAt = NOW.minus(Duration.ofHours(3));

        assertEquals(lastSyncedAt.minus(Duration.ofMinutes(15)),
                MetaSyncProperties.defaults().since(lastSyncedAt, NOW));
    }

    @Test
    void anAccountNeverSyncedStartsAFirstWindowBack() {
        assertEquals(NOW.minus(Duration.ofDays(1)), MetaSyncProperties.defaults().since(null, NOW));
    }

    @Test
    void theBoundsCarrySinceAndCapTheTotalAtConversationsTimesMessages() {
        Instant since = NOW.minus(Duration.ofHours(1));

        assertEquals(new HistoryBounds(3, 4, 12, since),
                new MetaSyncProperties(true, null, 3, 4, null, null).bounds(since));
    }
}
