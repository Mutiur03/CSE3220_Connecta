package com.connecta.backend.org.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.service.ConversationWorkloadService;
import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.AvailabilityStatus;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.dto.AvailabilityResponse;
import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Roles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether each member takes new conversations. A member sets their own status (any role); only an ADMIN
 * lists everyone's. A member without a row counts as AWAY. Rows are always read by membership AND
 * organization, and the caller must belong to the organization, so another tenant reads as not found.
 */
@Service
@RequiredArgsConstructor
public class AgentAvailabilityService {

    private static final Sort BY_NAME = Sort.by("user.fullName").ascending().and(Sort.by("id"));

    private final AgentAvailabilityRepository availabilities;
    private final MembershipRepository memberships;
    private final ConversationWorkloadService workload;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;

    /** Any member: their own status, AWAY when they never set one. */
    @Transactional(readOnly = true)
    public AvailabilityResponse getOwn(String subject, UUID organizationId) {
        Membership me = tenants.requireMembership(organizationId, activeCaller.load(subject).getId());
        return AvailabilityResponse.from(me,
                availabilities.findByMembershipIdAndOrganizationId(me.getId(), organizationId).orElse(null),
                openCount(organizationId, me));
    }

    /** Any member: creates the row on the first change, updates it afterwards. */
    @Transactional
    public AvailabilityResponse setOwn(String subject, UUID organizationId, AvailabilityStatus status) {
        Membership me = tenants.requireMembership(organizationId, activeCaller.load(subject).getId());
        AgentAvailability row = availabilities.findByMembershipIdAndOrganizationId(me.getId(), organizationId)
                .orElseGet(() -> new AgentAvailability(me, status));
        row.changeStatus(status);
        return AvailabilityResponse.from(me, availabilities.saveAndFlush(row), openCount(organizationId, me));
    }

    /** ADMIN or above: every member of the organization with their status. */
    @Transactional(readOnly = true)
    public PageResponse<AvailabilityResponse> list(String subject, UUID organizationId, int page, int size) {
        tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        Page<Membership> found = memberships.findByOrganizationId(organizationId, PageRequest.of(page, size, BY_NAME));
        Map<UUID, AgentAvailability> byMembership = availabilities
                .findByOrganizationIdAndMembershipIdIn(organizationId,
                        found.getContent().stream().map(Membership::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getMembership().getId(), Function.identity()));
        Map<UUID, Long> open = workload.openCounts(organizationId,
                found.getContent().stream().map(Membership::getId).toList());
        return PageResponse.from(found,
                m -> AvailabilityResponse.from(m, byMembership.get(m.getId()), open.get(m.getId())));
    }

    private long openCount(UUID organizationId, Membership member) {
        return workload.openCounts(organizationId, List.of(member.getId())).get(member.getId());
    }
}
