package com.connecta.backend.org.repository;

import com.connecta.backend.org.domain.AgentAvailability;
import com.connecta.backend.org.domain.AvailabilityStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface AgentAvailabilityRepository extends JpaRepository<AgentAvailability, UUID> {

    /** A member's availability only inside the organization, so another tenant's member reads as absent. */
    Optional<AgentAvailability> findByMembershipIdAndOrganizationId(UUID membershipId, UUID organizationId);

    /** The AVAILABLE rows of one user across all of their memberships. */
    List<AgentAvailability> findByMembershipUserIdAndStatus(UUID userId, AvailabilityStatus status);

    /** The rows of these members inside the organization, so a page of members needs one query. */
    @EntityGraph(attributePaths = "membership")
    List<AgentAvailability> findByOrganizationIdAndMembershipIdIn(UUID organizationId, Collection<UUID> membershipIds);

    /**
     * The one AVAILABLE member of the organization with an enabled account who waited longest (never
     * assigned first, then by id), row-locked so concurrent callers skip it instead of choosing it too.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select a from AgentAvailability a join a.membership m join m.user u
            where a.organization.id = :organizationId
              and a.status = com.connecta.backend.org.domain.AvailabilityStatus.AVAILABLE
              and u.enabled = true
            order by a.lastAssignedAt asc nulls first, a.id asc
            limit 1
            """)
    List<AgentAvailability> lockNextAvailable(@Param("organizationId") UUID organizationId);
}
