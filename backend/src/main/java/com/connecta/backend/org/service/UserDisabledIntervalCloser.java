package com.connecta.backend.org.service;

import com.connecta.backend.user.service.UserDisabledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Observer: a disabled user stops being available, so their open intervals end with the change. */
@Component
@RequiredArgsConstructor
public class UserDisabledIntervalCloser {

    private final AvailabilityIntervalService intervals;

    @EventListener
    public void onUserDisabled(UserDisabledEvent event) {
        intervals.closeAllForUser(event.userId());
    }
}
