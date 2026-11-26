package com.connecta.backend.org.service;

import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.repository.AvailabilityIntervalRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records when a member starts and stops being online. Opening and closing are both idempotent. */
@Service
@RequiredArgsConstructor
public class AvailabilityIntervalService {

    private final AvailabilityIntervalRepository intervals;
    private final Clock clock;

    @Transactional
    public void open(Membership member) {
        if (findOpen(member).isEmpty()) {
            intervals.save(new AvailabilityInterval(member, clock.instant()));
        }
    }

    /** Closes the member's open interval, if any, as of {@code endedAt}. */
    @Transactional
    public void close(Membership member, Instant endedAt) {
        findOpen(member).ifPresent(interval -> interval.close(endedAt));
    }

    @Transactional
    public void closeAllForUser(UUID userId) {
        Instant now = clock.instant();
        intervals.findByMembershipUserIdAndEndedAtIsNull(userId).forEach(interval -> interval.close(now));
    }

    /** Closes every interval a stop left open, ending each at the last heartbeat that saw its member. */
    @Transactional
    public void closeAllStale() {
        intervals.closeAllOpenAtLastSeen();
    }

    /** Marks the given members' open intervals as seen now: one statement however many members. */
    @Transactional
    public void heartbeat(Collection<UUID> membershipIds) {
        intervals.touchOpen(membershipIds, clock.instant());
    }

    private Optional<AvailabilityInterval> findOpen(Membership member) {
        return intervals.findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
                member.getId(), member.getOrganization().getId());
    }
}
