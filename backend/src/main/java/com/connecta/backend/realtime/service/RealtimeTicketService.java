package com.connecta.backend.realtime.service;

import com.connecta.backend.config.RealtimeProperties;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.realtime.dto.TicketResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Hands out short-lived, single-use tickets so a browser (which cannot set an Authorization header on a
 * WebSocket) can connect without a long-lived token in the URL. Issuing checks the caller's membership;
 * redeeming removes the ticket, so a ticket works once and only until it expires.
 *
 * <p>ponytail: tickets live in this process's memory, so a ticket must be redeemed on the instance that
 * issued it. A shared store (and a broker for the push) is the step for more than one instance.
 */
@Service
@RequiredArgsConstructor
public class RealtimeTicketService {

    private static final int TICKET_BYTES = 32;

    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final RealtimeProperties properties;
    private final Clock clock;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, RealtimeGrant> grants = new ConcurrentHashMap<>();

    /** A ticket for the caller and organization. A non-member (or unknown organization) gets NotAMemberException. */
    public TicketResponse issue(String subject, UUID organizationId) {
        UUID userId = activeCaller.load(subject).getId();
        tenants.requireMembership(organizationId, userId);
        Instant now = clock.instant();
        grants.values().removeIf(grant -> !grant.expiresAt().isAfter(now));
        String ticket = newTicket();
        grants.put(ticket, new RealtimeGrant(organizationId, userId, now.plus(properties.ticketTtl())));
        return new TicketResponse(ticket, properties.ticketTtl().toSeconds());
    }

    /** Consumes the ticket. Empty when it is unknown, already used or expired. */
    public Optional<RealtimeGrant> redeem(String ticket) {
        if (ticket == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(grants.remove(ticket))
                .filter(grant -> grant.expiresAt().isAfter(clock.instant()));
    }

    private String newTicket() {
        byte[] bytes = new byte[TICKET_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
