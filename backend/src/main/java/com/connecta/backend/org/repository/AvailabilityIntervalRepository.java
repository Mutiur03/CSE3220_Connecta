package com.connecta.backend.org.repository;

import com.connecta.backend.org.domain.AvailabilityInterval;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityIntervalRepository extends JpaRepository<AvailabilityInterval, UUID> {

    /** The member's open interval only inside the organization, so another tenant's member reads as absent. */
    Optional<AvailabilityInterval> findByMembershipIdAndOrganizationIdAndEndedAtIsNull(
            UUID membershipId, UUID organizationId);

    /** Every open interval of one user across all of their memberships. */
    List<AvailabilityInterval> findByMembershipUserIdAndEndedAtIsNull(UUID userId);

    /** Ends every open interval at its last sighting, or at its start when it has none. */
    @Modifying
    @Query("""
            update AvailabilityInterval i set i.endedAt = coalesce(i.lastSeenAt, i.startedAt)
            where i.endedAt is null""")
    int closeAllOpenAtLastSeen();

    /** Stamps the open intervals of the given members as seen at {@code seenAt}, in one statement. */
    @Modifying
    @Query("""
            update AvailabilityInterval i set i.lastSeenAt = :seenAt
            where i.membership.id in :membershipIds and i.endedAt is null""")
    int touchOpen(@Param("membershipIds") Collection<UUID> membershipIds, @Param("seenAt") Instant seenAt);

    /** Intervals of the given members that overlap [from, to), membership loaded in the same query. */
    @EntityGraph(attributePaths = "membership")
    @Query("""
            select i from AvailabilityInterval i
            where i.organization.id = :organizationId and i.membership.id in :membershipIds
              and i.startedAt < :to and (i.endedAt is null or i.endedAt > :from)""")
    List<AvailabilityInterval> findOverlapping(@Param("organizationId") UUID organizationId,
            @Param("membershipIds") Collection<UUID> membershipIds, @Param("from") Instant from,
            @Param("to") Instant to);
}
