package com.connecta.backend.org.service;

import static org.mockito.Mockito.verify;

import com.connecta.backend.user.service.UserDisabledEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserDisabledIntervalCloserTests {

    @Test
    void closesTheDisabledUsersIntervals() {
        AvailabilityIntervalService intervals = org.mockito.Mockito.mock(AvailabilityIntervalService.class);
        UUID userId = UUID.randomUUID();

        new UserDisabledIntervalCloser(intervals).onUserDisabled(new UserDisabledEvent(userId));

        verify(intervals).closeAllForUser(userId);
    }
}
