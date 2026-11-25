package com.connecta.backend.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.RealtimeProperties;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.NotAMemberException;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.realtime.dto.TicketResponse;
import com.connecta.backend.support.MutableClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RealtimeTicketServiceTests {

    private final UUID orgId = UUID.randomUUID();
    private final User user = TestUsers.user("agent@example.com", Roles.AGENT);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-24T10:00:00Z"));

    @Mock
    TenantAccessService tenants;

    @Mock
    ActiveCaller activeCaller;

    RealtimeTicketService service;

    @BeforeEach
    void setUp() {
        RealtimeProperties props = new RealtimeProperties(Duration.ofSeconds(30), Duration.ofSeconds(90),
                Duration.ofSeconds(30), 5, Duration.ofSeconds(5), 65536, 2, 3, Duration.ofMinutes(1), 1024);
        service = new RealtimeTicketService(tenants, activeCaller, props, clock);
    }

    @Nested
    class Issue {

        @Test
        void aMemberGetsATicketWithItsLifetime() {
            when(activeCaller.load(user.getId().toString())).thenReturn(user);

            TicketResponse response = service.issue(user.getId().toString(), orgId);

            assertThat(response.ticket()).hasSizeGreaterThanOrEqualTo(40);
            assertThat(response.expiresInSeconds()).isEqualTo(30);
        }

        @Test
        void aNonMemberIsRejectedAndGetsNoTicket() {
            when(activeCaller.load(user.getId().toString())).thenReturn(user);
            doThrow(new NotAMemberException()).when(tenants).requireMembership(orgId, user.getId());

            assertThatThrownBy(() -> service.issue(user.getId().toString(), orgId))
                    .isInstanceOf(NotAMemberException.class);
        }

        @Test
        void ticketsAreUnique() {
            when(activeCaller.load(user.getId().toString())).thenReturn(user);

            assertThat(service.issue(user.getId().toString(), orgId).ticket())
                    .isNotEqualTo(service.issue(user.getId().toString(), orgId).ticket());
        }
    }

    @Nested
    class Limits {

        private String issue() {
            return service.issue(user.getId().toString(), orgId).ticket();
        }

        @BeforeEach
        void caller() {
            when(activeCaller.load(user.getId().toString())).thenReturn(user);
        }

        @Test
        void aUserOverTheOutstandingCapIsRefused() {
            issue();
            issue();

            assertThatThrownBy(this::issue).isInstanceOf(TooManyTicketsException.class);
        }

        @Test
        void redeemingATicketFreesASlot() {
            String first = issue();
            issue();
            service.redeem(first);

            assertThat(issue()).isNotBlank();
        }

        @Test
        void expiryFreesSlotsButTheRateWindowStillApplies() {
            issue();
            issue();
            clock.advance(Duration.ofSeconds(30));
            issue();

            assertThatThrownBy(this::issue).isInstanceOf(TooManyTicketsException.class);
        }

        @Test
        void theRateWindowEndsAndAllowsMoreTickets() {
            for (int i = 0; i < 3; i++) {
                service.redeem(issue());
            }
            assertThatThrownBy(this::issue).isInstanceOf(TooManyTicketsException.class);

            clock.advance(Duration.ofMinutes(1));

            assertThat(issue()).isNotBlank();
        }

        @Test
        void theCapIsPerUser() {
            User other = TestUsers.user("other@example.com", Roles.AGENT);
            when(activeCaller.load(other.getId().toString())).thenReturn(other);
            issue();
            issue();

            assertThat(service.issue(other.getId().toString(), orgId).ticket()).isNotBlank();
        }

        @Test
        void sweepForgetsExpiredTicketsSoTheyCannotBeRedeemed() {
            String ticket = issue();
            clock.advance(Duration.ofMinutes(2));

            service.sweepExpired();

            assertThat(service.redeem(ticket)).isEmpty();
            assertThat(issue()).isNotBlank();
        }
    }

    @Nested
    class Redeem {

        @Test
        void aTicketIsBoundToItsOrganizationAndUser() {
            when(activeCaller.load(user.getId().toString())).thenReturn(user);
            String ticket = service.issue(user.getId().toString(), orgId).ticket();

            RealtimeGrant grant = service.redeem(ticket).orElseThrow();

            assertThat(grant.organizationId()).isEqualTo(orgId);
            assertThat(grant.userId()).isEqualTo(user.getId());
        }

        @Test
        void aTicketWorksOnlyOnce() {
            when(activeCaller.load(user.getId().toString())).thenReturn(user);
            String ticket = service.issue(user.getId().toString(), orgId).ticket();

            assertThat(service.redeem(ticket)).isPresent();
            assertThat(service.redeem(ticket)).isEmpty();
        }

        @Test
        void anExpiredTicketIsRefused() {
            when(activeCaller.load(user.getId().toString())).thenReturn(user);
            String ticket = service.issue(user.getId().toString(), orgId).ticket();

            clock.advance(Duration.ofSeconds(30));

            assertThat(service.redeem(ticket)).isEmpty();
        }

        @Test
        void anUnknownOrMissingTicketIsRefused() {
            assertThat(service.redeem("nope")).isEmpty();
            assertThat(service.redeem(null)).isEmpty();
        }
    }
}
