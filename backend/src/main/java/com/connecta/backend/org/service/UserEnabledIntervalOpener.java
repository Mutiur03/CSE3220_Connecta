package com.connecta.backend.org.service;

import com.connecta.backend.user.service.UserEnabledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Observer: a re-enabled user who is still marked AVAILABLE starts counting available time again. */
@Component
@RequiredArgsConstructor
public class UserEnabledIntervalOpener {

    private final AvailabilityIntervalService intervals;

    @EventListener
    public void onUserEnabled(UserEnabledEvent event) {
        intervals.openAllAvailableForUser(event.userId());
    }
}
