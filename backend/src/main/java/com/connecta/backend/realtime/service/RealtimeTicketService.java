package com.connecta.backend.realtime.service;

import com.connecta.backend.config.RealtimeProperties;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.realtime.dto.TicketResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Hands out short-lived, single-use tickets so a browser (which cannot set an Authorization header on a
 * WebSocket) can connect without a long-lived token in the URL. Issuing checks the caller's membership and
 * caps both the unredeemed tickets a user holds and how fast they can ask for more; redeeming removes the
 * ticket, so a ticket works once and only until it expires.
 *
 * <p>Limitation: tickets live in this process's memory, so a ticket must be redeemed on the instance that
 * issued it. Horizontal scaling needs a shared ticket store (and a message broker for the push).
 */
@Service
@RequiredArgsConstructor
public class RealtimeTicketService {

    private static final int TICKET_BYTES = 32;

    /** One user's unredeemed tickets and recent issue times. Only touched inside {@code perUser.compute*}. */
    private static final class UserTickets {
        final Set<String> outstanding = new HashSet<>();
        final Deque<Instant> issued = new ArrayDeque<>();
    }

    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final RealtimeProperties properties;
    private final Clock clock;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, RealtimeGrant> grants = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UserTickets> perUser = new ConcurrentHashMap<>();

    /**
     * A ticket for the caller and organization. A non-member (or unknown organization) gets
     * NotAMemberException; a user over the ticket cap or rate gets TooManyTicketsException.
     */
    public TicketResponse issue(String subject, UUID organizationId) {
        UUID userId = activeCaller.load(subject).getId();
        tenants.requireMembership(organizationId, userId);
        Instant now = clock.instant();
        String ticket = newTicket();
        perUser.compute(userId, (id, state) -> {
            UserTickets tickets = state == null ? new UserTickets() : state;
            prune(tickets, now);
            if (tickets.outstanding.size() >= properties.maxOutstandingTickets()
                    || tickets.issued.size() >= properties.maxTicketsPerWindow()) {
                throw new TooManyTicketsException();
            }
            grants.put(ticket, new RealtimeGrant(organizationId, userId, now.plus(properties.ticketTtl())));
            tickets.outstanding.add(ticket);
            tickets.issued.addLast(now);
            return tickets;
        });
        return new TicketResponse(ticket, properties.ticketTtl().toSeconds());
    }

    /** Consumes the ticket. Empty when it is unknown, already used or expired. */
    public Optional<RealtimeGrant> redeem(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        Optional<RealtimeGrant> grant = Optional.ofNullable(grants.remove(ticket));
        grant.ifPresent(g -> perUser.computeIfPresent(g.userId(), (id, state) -> {
            state.outstanding.remove(ticket);
            return state;
        }));
        return grant.filter(g -> g.expiresAt().isAfter(clock.instant()));
    }

    /** Drops expired tickets and forgets users with nothing left. Runs on a schedule, not on every issue. */
    @Scheduled(fixedDelayString = "${app.realtime.sweep-interval:30s}")
    public void sweepExpired() {
        Instant now = clock.instant();
        grants.values().removeIf(grant -> !grant.expiresAt().isAfter(now));
        for (UUID userId : perUser.keySet()) {
            perUser.computeIfPresent(userId, (id, state) -> {
                prune(state, now);
                return state.outstanding.isEmpty() && state.issued.isEmpty() ? null : state;
            });
        }
    }

    private void prune(UserTickets state, Instant now) {
        state.outstanding.removeIf(ticket -> {
            RealtimeGrant grant = grants.get(ticket);
            return grant == null || !grant.expiresAt().isAfter(now);
        });
        Instant windowStart = now.minus(properties.ticketRateWindow());
        while (!state.issued.isEmpty() && !state.issued.peekFirst().isAfter(windowStart)) {
            state.issued.removeFirst();
        }
    }

    private String newTicket() {
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
