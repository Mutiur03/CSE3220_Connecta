package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.meta.service.HistoryBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MetaSyncPropertiesTests {

    @Test
    void missingOrNonPositiveValuesFallBackToSmallBoundedDefaults() {
        MetaSyncProperties p = new MetaSyncProperties(null, Duration.ZERO, -1, 0);

        assertTrue(p.enabled());
        assertEquals(Duration.ofMinutes(10), p.interval());
        assertEquals(10, p.maxConversations());
        assertEquals(10, p.messagesPerConversation());
    }

    @Test
    void explicitValuesAreKept() {
        MetaSyncProperties p = new MetaSyncProperties(false, Duration.ofMinutes(1), 3, 4);

        assertFalse(p.enabled());
        assertEquals(Duration.ofMinutes(1), p.interval());
        assertEquals(3, p.maxConversations());
    }

    @Test
    void theBoundsCapTheTotalAtConversationsTimesMessages() {
        assertEquals(new HistoryBounds(3, 4, 12), new MetaSyncProperties(true, null, 3, 4).bounds());
    }

    @Test
    void theSyncBoundsAreSmallerThanAFullImport() {
        HistoryBounds sync = MetaSyncProperties.defaults().bounds();
        HistoryBounds full = HistoryBounds.of(HistoryImportProperties.defaults());

        assertTrue(sync.maxConversations() < full.maxConversations());
        assertTrue(sync.messagesPerConversation() < full.messagesPerConversation());
        assertTrue(sync.maxMessages() < full.maxMessages());
    }
}
