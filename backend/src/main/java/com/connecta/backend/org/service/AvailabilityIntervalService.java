package com.connecta.backend.org.service;

import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.domain.AvailabilityStatus;
import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import com.connecta.backend.org.repository.AvailabilityIntervalRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records when a member starts and stops being AVAILABLE. Opening and closing are both idempotent. */
@Service
@RequiredArgsConstructor
public class AvailabilityIntervalService {

    private final AvailabilityIntervalRepository intervals;
    private final AgentAvailabilityRepository availabilities;
    private final Clock clock;

    @Transactional
    public void open(Membership member) {
        if (findOpen(member).isEmpty()) {
            intervals.save(new AvailabilityInterval(member, clock.instant()));
        }
    }

    @Transactional
    public void close(Membership member) {
        findOpen(member).ifPresent(interval -> interval.close(clock.instant()));
    }

    @Transactional
    public void closeAllForUser(UUID userId) {
        Instant now = clock.instant();
        intervals.findByMembershipUserIdAndEndedAtIsNull(userId).forEach(interval -> interval.close(now));
    }

    /** For a re-enabled user: opens an interval for each membership still marked AVAILABLE. */
    @Transactional
    public void openAllAvailableForUser(UUID userId) {
        availabilities.findByMembershipUserIdAndStatus(userId, AvailabilityStatus.AVAILABLE)
                .forEach(row -> open(row.getMembership()));
    }

    private Optional<AvailabilityInterval> findOpen(Membership member) {
        return intervals.findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
                member.getId(), member.getOrganization().getId());
    }
}
