package com.connecta.backend.org.repository;

import com.connecta.backend.org.domain.AvailabilityInterval;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityIntervalRepository extends JpaRepository<AvailabilityInterval, UUID> {

    /** The member's open interval only inside the organization, so another tenant's member reads as absent. */
    Optional<AvailabilityInterval> findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
            UUID membershipId, UUID organizationId);

    /** Every open interval of one user across all of their memberships. */
    List<AvailabilityInterval> findByMembershipUserIdAndEndedAtIsNull(UUID userId);
}
