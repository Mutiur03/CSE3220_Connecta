package com.connecta.backend.org.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.AvailabilityInterval;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.dto.AvailabilityReportResponse;
import com.connecta.backend.org.dto.AvailabilityReportResponse.MemberReport;
import com.connecta.backend.org.repository.AvailabilityIntervalRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Roles;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Time each member was AVAILABLE per UTC day. ADMIN or above only. Reads members and intervals only inside
 * the organization, so another tenant reads as not found.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityReportService {

    public static final int MAX_DAYS = 31;

    private static final Sort BY_NAME = Sort.by("user.fullName").ascending().and(Sort.by("id"));

    private final MembershipRepository memberships;
    private final AvailabilityIntervalRepository intervals;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AvailabilityReportResponse report(String subject, UUID organizationId, LocalDate from, LocalDate to,
            int page, int size) {
        tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        requireValidRange(from, to);
        Page<Membership> found = memberships.findByOrganizationId(organizationId, PageRequest.of(page, size, BY_NAME));
        int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        Map<UUID, long[]> perMember = secondsPerDay(organizationId, found.getContent(), from, to, days);
        PageResponse<MemberReport> content = PageResponse.from(found, m -> MemberReport.from(m.getId(),
                m.getUser().getId(), m.getUser().getFullName(), m.getRole().getName(), from,
                perMember.getOrDefault(m.getId(), new long[days])));
        return AvailabilityReportResponse.from(from, to, content);
    }

    private Map<UUID, long[]> secondsPerDay(UUID organizationId, List<Membership> members, LocalDate from,
            LocalDate to, int days) {
        Map<UUID, long[]> perMember = new HashMap<>();
        if (members.isEmpty()) {
            return perMember;
        }
        Instant rangeStart = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant rangeEnd = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant now = clock.instant();
        List<UUID> ids = members.stream().map(Membership::getId).toList();
        for (AvailabilityInterval i : intervals.findOverlapping(organizationId, ids, rangeStart, rangeEnd)) {
            long[] member = perMember.computeIfAbsent(i.getMembership().getId(), id -> new long[days]);
            DailyAvailability.add(member, from, i.getStartedAt(), i.getEndedAt(), now);
        }
        return perMember;
    }

    private static void requireValidRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidReportRangeException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidReportRangeException("A report covers at most " + MAX_DAYS + " days");
        }
    }
}
