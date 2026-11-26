package com.connecta.backend.org.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.connecta.backend.user.service.UserEnabledEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserEnabledIntervalOpenerTests {

    @Test
    void opensTheEnabledUsersAvailableIntervals() {
        AvailabilityIntervalService intervals = mock(AvailabilityIntervalService.class);
        UUID userId = UUID.randomUUID();

        new UserEnabledIntervalOpener(intervals).onUserEnabled(new UserEnabledEvent(userId));

        verify(intervals).openAllAvailableForUser(userId);
    }
}
