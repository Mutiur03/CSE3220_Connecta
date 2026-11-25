package com.connecta.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ViewOncePropertiesTests {

    @Test
    void defaultsKeepPhotosAndUseAShortWindow() {
        ViewOnceProperties p = ViewOnceProperties.defaults();

        assertFalse(p.hide());
        assertEquals(Duration.ofHours(1), p.markTtl());
        assertEquals(Duration.ofMinutes(10), p.cleanupInterval());
    }

    @Test
    void nonPositiveDurationsFallBackAndExplicitValuesAreKept() {
        assertEquals(Duration.ofHours(1), new ViewOnceProperties(true, Duration.ZERO, null).markTtl());
        ViewOnceProperties p = new ViewOnceProperties(true, Duration.ofMinutes(5), Duration.ofMinutes(1));

        assertTrue(p.hide());
        assertEquals(Duration.ofMinutes(5), p.markTtl());
    }
}
