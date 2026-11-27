package com.connecta.backend.org.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class StaleIntervalCloserTests {

    @Test
    void onStartupEveryLeftOverOpenIntervalIsClosedAtItsLastSighting() {
        AvailabilityIntervalService intervals = mock(AvailabilityIntervalService.class);

        new StaleIntervalCloser(intervals).onReady();

        verify(intervals).closeAllStale();
    }
}
