package com.connecta.backend.org.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.inbox.service.ConversationWorkloadService;
import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.dto.AssignableResponse;
import com.connecta.backend.org.dto.AvailabilityResponse;
import com.connecta.backend.org.repository.AgentAvailabilityRepository;
import com.connecta.backend.org.repository.MembershipRepository;
import com.connecta.backend.user.domain.Roles;
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
 * Whether each member takes new conversations. Only an ADMIN lists everyone's and sets the assignable
 * switch. A member without a row is not assignable. Rows are always read by membership AND
 * organization, and the caller must belong to the organization, so another tenant reads as not found.
 */
@Service
@RequiredArgsConstructor
public class AgentAvailabilityService {

    private static final Sort BY_NAME = Sort.by("user.fullName").ascending().and(Sort.by("id"));

    private final AgentAvailabilityRepository availabilities;
    private final MembershipRepository memberships;
    private final PresenceService presence;
    private final ConversationWorkloadService workload;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;

    /** ADMIN or above: lets a member of the organization receive automatic assignments, or not. */
    @Transactional
    public AssignableResponse setAssignable(String subject, UUID organizationId, UUID membershipId,
            boolean assignable) {
        tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
        Membership member = memberships.findByIdAndOrganizationId(membershipId, organizationId)
                .orElseThrow(MemberNotFoundException::new);
        AgentAvailability row = availabilities.findByMembershipIdAndOrganizationId(membershipId, organizationId)
                .orElseGet(() -> createRow(member));
        row.changeAssignable(assignable);
        return new AssignableResponse(membershipId, availabilities.saveAndFlush(row).isAssignable());
    }

    /** Insert-if-absent, then read: a concurrent first toggle that won the insert is simply reused. */
    private AgentAvailability createRow(Membership member) {
        UUID organizationId = member.getOrganization().getId();
        availabilities.insertIfAbsent(organizationId, member.getId());
        return availabilities.findByMembershipIdAndOrganizationId(member.getId(), organizationId).orElseThrow();
    }

    /** ADMIN or above: every member of the organization with their availability. */
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
                m -> AvailabilityResponse.from(m, byMembership.get(m.getId()), open.get(m.getId()),
                        isOnline(organizationId, m)));
    }

    private boolean isOnline(UUID organizationId, Membership member) {
        return presence.isOnline(organizationId, member.getUser().getId());
    }
}
