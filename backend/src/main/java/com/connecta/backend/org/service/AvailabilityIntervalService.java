package com.connecta.backend.org.service;

import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.repository.AvailabilityIntervalRepository;
import java.time.Clock;
import java.time.Instant;
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

    /**
     * Closes every interval left open by a stop or crash, ending each where it started. A crash therefore
     * undercounts that stretch of time rather than overcounting it.
     */
    @Transactional
    public void closeAllStale() {
        intervals.closeAllOpenAtStart();
    }

    private Optional<AvailabilityInterval> findOpen(Membership member) {
        return intervals.findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
                member.getId(), member.getOrganization().getId());
    }
}
