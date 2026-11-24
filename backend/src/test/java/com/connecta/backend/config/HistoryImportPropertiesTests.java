package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HistoryImportPropertiesTests {

    @Test
    void missingOrNonPositiveValuesFallBackToBoundedDefaults() {
        HistoryImportProperties p = new HistoryImportProperties(null, 0, -1, 0, 0, Duration.ZERO, 0, -5, 0);

        assertTrue(p.enabled());
        assertEquals(50, p.maxConversations());
        assertEquals(50, p.messagesPerConversation());
        assertEquals(1000, p.maxMessages());
        assertEquals(25, p.pageSize());
        assertEquals(Duration.ofSeconds(15), p.requestTimeout());
        assertEquals(2, p.workerThreads());
        assertEquals(100, p.queueCapacity());
        assertEquals(200, p.maxGraphPages());
    }

    @Test
    void explicitValuesAreKept() {
        HistoryImportProperties p = new HistoryImportProperties(false, 3, 4, 5, 6, Duration.ofSeconds(2), 7, 8, 9);

        assertFalse(p.enabled());
        assertEquals(3, p.maxConversations());
        assertEquals(8, p.queueCapacity());
        assertEquals(9, p.maxGraphPages());
    }
}
