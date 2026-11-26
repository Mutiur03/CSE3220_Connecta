package com.connecta.backend.org.repository;

import com.connecta.backend.org.domain.AgentAvailability;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAvailabilityRepository extends JpaRepository<AgentAvailability, UUID> {

    /** A member's availability only inside the organization, so another tenant's member reads as absent. */
    Optional<AgentAvailability> findByMembershipIdAndOrganizationId(UUID membershipId, UUID organizationId);

    /** The rows of these members inside the organization, so a page of members needs one query. */
    @EntityGraph(attributePaths = "membership")
    List<AgentAvailability> findByOrganizationIdAndMembershipIdIn(UUID organizationId, Collection<UUID> membershipIds);
}
