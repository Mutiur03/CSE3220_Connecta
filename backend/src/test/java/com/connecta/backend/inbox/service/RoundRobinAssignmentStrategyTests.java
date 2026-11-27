package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.connecta.backend.inbox.domain.Channel;
import com.connecta.backend.inbox.domain.Conversation;
import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.Organization;
import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import com.connecta.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RoundRobinAssignmentStrategyTests {

    private final Organization org = new Organization("Org", "org");
    private final Conversation conversation = new Conversation(org, Channel.MESSENGER, "thread");
    private final AgentAvailabilityRepository repository = mock(AgentAvailabilityRepository.class);
    private final AtomicLong tick = new AtomicLong(1000);
    private final Clock clock = mock(Clock.class);
    private RoundRobinAssignmentStrategy strategy;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenAnswer(call -> Instant.ofEpochSecond(tick.incrementAndGet()));
        strategy = new RoundRobinAssignmentStrategy(repository, clock);
    }

    private AgentAvailability row(String name, boolean assignable, boolean enabled, Instant lastAssigned) {
        User user = new User(name + "@example.com", "hash", name);
        if (!enabled) {
            user.disable();
        }
        AgentAvailability row = new AgentAvailability(new Membership(org, user, null));
        row.changeAssignable(assignable);
        if (lastAssigned != null) {
            row.recordAssignmentAt(lastAssigned);
        }
        return row;
    }

    /** In-memory twin of the repository query: eligible rows, never-assigned first, then oldest. */
    private void fakeRepository(List<AgentAvailability> rows) {
        when(repository.lockNextAvailable(any())).thenAnswer(call -> rows.stream()
                .filter(r -> r.isAssignable() && r.getMembership().getUser().isEnabled())
                .sorted(Comparator.comparing(AgentAvailability::getLastAssignedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(1).toList());
    }

    @Nested
    class Choosing {

        @Test
        void anAssignableMemberIsChosen() {
            AgentAvailability assignable = row("away", true, true, null);
            fakeRepository(List.of(assignable));

            assertSame(assignable.getMembership(), strategy.choose(conversation).orElseThrow());
        }

        @Test
        void picksTheRowTheQueryReturnsAndRecordsTheTime() {
            AgentAvailability first = row("a", true, true, null);
            when(repository.lockNextAvailable(any())).thenReturn(List.of(first));

            Optional<Membership> chosen = strategy.choose(conversation);

            assertSame(first.getMembership(), chosen.orElseThrow());
            assertEquals(Instant.ofEpochSecond(1001), first.getLastAssignedAt());
        }

        @Test
        void neverAssignedComesBeforeAnOlderAssignment() {
            AgentAvailability old = row("old", true, true, Instant.ofEpochSecond(5));
            AgentAvailability fresh = row("fresh", true, true, null);
            fakeRepository(List.of(old, fresh));

            assertSame(fresh.getMembership(), strategy.choose(conversation).orElseThrow());
        }

        @Test
        void oldestAssignmentWins() {
            AgentAvailability newer = row("newer", true, true, Instant.ofEpochSecond(50));
            AgentAvailability older = row("older", true, true, Instant.ofEpochSecond(5));
            fakeRepository(List.of(newer, older));

            assertSame(older.getMembership(), strategy.choose(conversation).orElseThrow());
        }

        @Test
        void skipsNonAssignableAndDisabledMembers() {
            AgentAvailability notAssignable = row("away", false, true, null);
            AgentAvailability disabled = row("off", true, false, null);
            AgentAvailability ok = row("ok", true, true, Instant.ofEpochSecond(9));
            fakeRepository(List.of(notAssignable, disabled, ok));

            assertSame(ok.getMembership(), strategy.choose(conversation).orElseThrow());
        }

        @Test
        void aMemberWithoutAnAvailabilityRowIsNeverChosen() {
            fakeRepository(List.of());

            assertTrue(strategy.choose(conversation).isEmpty());
        }

        @Test
        void isEmptyWhenNobodyIsAssignable() {
            fakeRepository(List.of(row("away", false, true, null)));

            assertTrue(strategy.choose(conversation).isEmpty());
        }
    }

    @Test
    void rotatesFairlyAcrossThreeAgentsOverSixAssignments() {
        List<AgentAvailability> rows = List.of(row("a", true, true, null),
                row("b", true, true, null),
                row("c", true, true, null));
        fakeRepository(rows);

        List<Membership> order = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            order.add(strategy.choose(conversation).orElseThrow());
        }

        assertEquals(3, order.subList(0, 3).stream().distinct().count());
        assertEquals(order.subList(0, 3), order.subList(3, 6));
    }

    @Test
    void reportsItsMode() {
        assertEquals(AssignmentMode.ROUND_ROBIN, strategy.mode());
    }
}
