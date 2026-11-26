package com.connecta.backend.org.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AvailabilityIntervalTests {

    private static final Instant START = Instant.parse("2026-11-15T10:00:00Z");

    private final Organization organization = new Organization("Acme", "acme");
    private final Membership member =
            new Membership(organization, TestUsers.user("a@example.com", "AGENT"), new Role("AGENT"));

    @Test
    void closeRecordsTheEnd() {
        AvailabilityInterval interval = new AvailabilityInterval(member, START);

        interval.close(START.plusSeconds(60));

        assertEquals(START.plusSeconds(60), interval.getEndedAt());
    }

    @Test
    void closeRejectsAnEarlierEnd() {
        AvailabilityInterval interval = new AvailabilityInterval(member, START);

        assertThrows(IllegalArgumentException.class, () -> interval.close(START.minusSeconds(1)));
    }
}
