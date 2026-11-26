package com.connecta.backend.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.dto.AvailabilityReportResponse;
import com.connecta.backend.org.dto.AvailabilityReportResponse.MemberReport;
import com.connecta.backend.org.repository.AvailabilityIntervalRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.support.TestClock;
import com.connecta.backend.support.TestUsers;
import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import com.connecta.backend.user.service.UserService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;

/** The real TenantAccessService runs, so the role rule is the one under test. Clock is 2026-11-15T12:00Z. */
@ExtendWith(MockitoExtension.class)
class AvailabilityReportServiceTests {

    private static final long HOUR = 3600;
    private static final LocalDate FROM = LocalDate.parse("2026-11-13");
    private static final LocalDate TO = LocalDate.parse("2026-11-16");

    @Mock
    MembershipRepository memberships;

    @Mock
    AvailabilityIntervalRepository intervals;

    @Mock
    UserService users;

    AvailabilityReportService service;

    private final UUID organizationId = UUID.randomUUID();
    private final Organization organization = new Organization("Acme", "acme");
    private final User caller = TestUsers.user("caller@example.com", Roles.ADMIN);
    private final String subject = caller.getId().toString();

    @BeforeEach
    void setUp() {
        TestUsers.set(organization, "id", organizationId);
        TenantAccessService tenants = new TenantAccessService(memberships, RoleHierarchyImpl.fromHierarchy(
                "ROLE_OWNER > ROLE_ADMIN\nROLE_ADMIN > ROLE_AGENT"));
        service = new AvailabilityReportService(memberships, intervals, tenants, new ActiveCaller(users),
                TestClock.FIXED);
        lenient().when(users.findEnabledById(caller.getId())).thenReturn(Optional.of(caller));
    }

    private Membership member(User user, String roleName) {
        Membership m = new Membership(organization, user, new Role(roleName));
        TestUsers.set(m, "id", UUID.randomUUID());
        return m;
    }

    private Membership agent(String email) {
        return member(TestUsers.user(email, Roles.AGENT), Roles.AGENT);
    }

    private void callerIs(String roleName) {
        when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                .thenReturn(Optional.of(member(caller, roleName)));
    }

    private void membersAre(Membership... members) {
        when(memberships.findByOrganizationId(eq(organizationId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(members), PageRequest.of(0, 20), members.length));
    }

    private AvailabilityInterval interval(Membership m, String start, String end) {
        AvailabilityInterval i = new AvailabilityInterval(m, Instant.parse(start));
        if (end != null) {
            i.close(Instant.parse(end));
        }
        return i;
    }

    private void intervalsAre(AvailabilityInterval... found) {
        when(intervals.findOverlapping(eq(organizationId), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(found));
    }

    private AvailabilityReportResponse run() {
        return service.report(subject, organizationId, FROM, TO, 0, 20);
    }

    @Nested
    class Report {

        @Test
        void everyDayOfTheRangeIsPresentInOrderEvenWithNoIntervals() {
            callerIs(Roles.ADMIN);
            membersAre(agent("idle@example.com"));
            intervalsAre();

            MemberReport report = run().content().get(0);

            assertEquals(0, report.totalSeconds());
            assertEquals(List.of("2026-11-13", "2026-11-14", "2026-11-15", "2026-11-16"),
                    report.days().stream().map(d -> d.date().toString()).toList());
            assertEquals(0, report.days().stream().mapToLong(d -> d.availableSeconds()).sum());
        }

        @Test
        void aCrossMidnightIntervalSplitsAndTheTotalAddsUp() {
            callerIs(Roles.ADMIN);
            Membership agent = agent("a@example.com");
            membersAre(agent);
            intervalsAre(interval(agent, "2026-11-13T23:00:00Z", "2026-11-14T02:00:00Z"));

            MemberReport report = run().content().get(0);

            assertEquals(HOUR, report.days().get(0).availableSeconds());
            assertEquals(2 * HOUR, report.days().get(1).availableSeconds());
            assertEquals(3 * HOUR, report.totalSeconds());
        }

        @Test
        void multipleIntervalsOnADaySum() {
            callerIs(Roles.ADMIN);
            Membership agent = agent("a@example.com");
            membersAre(agent);
            intervalsAre(interval(agent, "2026-11-14T08:00:00Z", "2026-11-14T09:00:00Z"),
                    interval(agent, "2026-11-14T10:00:00Z", "2026-11-14T10:30:00Z"));

            assertEquals(5400, run().content().get(0).days().get(1).availableSeconds());
        }

        @Test
        void anOpenIntervalCountsToNowAndFutureDaysStayZero() {
            callerIs(Roles.ADMIN);
            Membership agent = agent("a@example.com");
            membersAre(agent);
            intervalsAre(interval(agent, "2026-11-15T10:00:00Z", null));

            MemberReport report = run().content().get(0);

            assertEquals(2 * HOUR, report.days().get(2).availableSeconds());
            assertEquals(0, report.days().get(3).availableSeconds());
        }

        @Test
        void intervalsAreSummedPerMemberNotMixed() {
            callerIs(Roles.ADMIN);
            Membership a = agent("a@example.com");
            Membership b = agent("b@example.com");
            membersAre(a, b);
            intervalsAre(interval(b, "2026-11-14T08:00:00Z", "2026-11-14T09:00:00Z"));

            List<MemberReport> content = run().content();

            assertEquals(0, content.get(0).totalSeconds());
            assertEquals(HOUR, content.get(1).totalSeconds());
            assertEquals(b.getId(), content.get(1).membershipId());
        }

        @Test
        void theResponseEchoesTheRangeAndPaging() {
            callerIs(Roles.ADMIN);
            membersAre();

            AvailabilityReportResponse response = run();

            assertEquals(FROM, response.from());
            assertEquals(TO, response.to());
            assertEquals(20, response.size());
        }

        @Test
        void anOwnerMayRunIt() {
            callerIs(Roles.OWNER);
            membersAre();

            assertEquals(0, run().content().size());
        }
    }

    @Nested
    class Rules {

        @Test
        void anAgentIsDenied() {
            callerIs(Roles.AGENT);

            assertThrows(InsufficientOrganizationRoleException.class, AvailabilityReportServiceTests.this::run);
            verifyNoInteractions(intervals);
        }

        @Test
        void aNonMemberIsNotFound() {
            when(memberships.findByOrganizationIdAndUserId(organizationId, caller.getId()))
                    .thenReturn(Optional.empty());

            assertThrows(NotAMemberException.class, AvailabilityReportServiceTests.this::run);
        }

        @Test
        void reversedDatesAreRejected() {
            callerIs(Roles.ADMIN);

            assertThrows(InvalidReportRangeException.class,
                    () -> service.report(subject, organizationId, TO, FROM, 0, 20));
        }

        @Test
        void moreThan31DaysIsRejectedAndExactly31IsAllowed() {
            callerIs(Roles.ADMIN);
            membersAre(agent("a@example.com"));
            intervalsAre();
            LocalDate start = LocalDate.parse("2026-10-01");

            assertThrows(InvalidReportRangeException.class,
                    () -> service.report(subject, organizationId, start, start.plusDays(31), 0, 20));
            AvailabilityReportResponse ok = service.report(subject, organizationId, start, start.plusDays(30), 0, 20);
            assertEquals(31, ok.content().get(0).days().size());
        }
    }
}
