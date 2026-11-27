package com.connecta.backend.org.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Observer: after a start no connection exists, so intervals a stop left open are closed at their last heartbeat. */
@Component
@RequiredArgsConstructor
public class StaleIntervalCloser {

    private final AvailabilityIntervalService intervals;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        intervals.closeAllStale();
    }
}
