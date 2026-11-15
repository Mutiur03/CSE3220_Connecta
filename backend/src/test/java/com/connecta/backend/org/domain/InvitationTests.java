package com.connecta.backend.org.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.support.TestClock;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class InvitationTests {

    private static final Instant CREATED = TestClock.NOW;
    private static final Instant EXPIRES = CREATED.plus(Duration.ofDays(7));

    private final User inviter = new User("owner@example.com", "hash", "Owner");

    private Invitation pending() {
        return new Invitation(new Organization("Acme", "acme"), "Jane@Example.com", new Role(Roles.AGENT),
                inviter, "a".repeat(64), CREATED, EXPIRES);
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, TestClock.FIXED.getZone());
    }

    @Nested
    class Creation {

        @Test
        void startsPendingWithANormalizedEmailAndNoAcceptanceTime() {
            Invitation invitation = pending();

            assertEquals(Invitation.Status.PENDING, invitation.getStatus());
            assertEquals("jane@example.com", invitation.getEmail());
            assertNull(invitation.getAcceptedAt());
        }
    }

    @Nested
    class Expiry {

        @Test
        void exactlyAtTheExpiryInstantIsAlreadyExpired() {
            assertFalse(pending().isExpired(EXPIRES.minusSeconds(1)));
            assertTrue(pending().isExpired(EXPIRES));
        }
    }

    @Nested
    class Accept {

        @Test
        void aPendingUnexpiredInvitationBecomesAccepted() {
            Invitation invitation = pending();

            invitation.accept(at(CREATED.plusSeconds(60)));

            assertEquals(Invitation.Status.ACCEPTED, invitation.getStatus());
            assertEquals(CREATED.plusSeconds(60), invitation.getAcceptedAt());
        }

        @Test
        void anExpiredInvitationIsNotAcceptable() {
            Invitation invitation = pending();

            assertThrows(InvitationNotAcceptableException.class, () -> invitation.accept(at(EXPIRES)));
            assertEquals(Invitation.Status.PENDING, invitation.getStatus());
        }

        @Test
        void aRevokedInvitationIsNotAcceptable() {
            Invitation invitation = pending();
            invitation.revoke();

            assertThrows(InvitationNotAcceptableException.class, () -> invitation.accept(at(CREATED)));
        }

        @Test
        void anAcceptedInvitationCannotBeAcceptedAgain() {
            Invitation invitation = pending();
            invitation.accept(at(CREATED));

            assertThrows(InvitationAlreadyAcceptedException.class, () -> invitation.accept(at(CREATED)));
        }
    }

    @Nested
    class Revoke {

        @Test
        void aPendingInvitationIsRevoked() {
            Invitation invitation = pending();

            invitation.revoke();

            assertEquals(Invitation.Status.REVOKED, invitation.getStatus());
        }

        @Test
        void revokingTwiceIsHarmless() {
            Invitation invitation = pending();
            invitation.revoke();

            invitation.revoke();

            assertEquals(Invitation.Status.REVOKED, invitation.getStatus());
        }

        @Test
        void anAcceptedInvitationCannotBeRevoked() {
            Invitation invitation = pending();
            invitation.accept(at(CREATED));

            assertThrows(InvitationAlreadyAcceptedException.class, invitation::revoke);
            assertEquals(Invitation.Status.ACCEPTED, invitation.getStatus());
        }
    }
}
