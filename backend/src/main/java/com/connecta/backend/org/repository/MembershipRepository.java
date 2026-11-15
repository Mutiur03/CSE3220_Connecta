package com.connecta.backend.org.repository;

import com.connecta.backend.org.domain.Membership;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    /** Every membership a user holds; loads the organization and role so callers can read them. */
    @EntityGraph(attributePaths = {"organization", "role"})
    List<Membership> findAllByUserId(UUID userId);

    /** One page of a user's memberships, with the organization loaded. The pageable carries the sort. */
    @EntityGraph(attributePaths = {"organization", "role"})
    Page<Membership> findByUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "role"})
    Optional<Membership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    /** One page of an organization's memberships, with the user and role loaded. The pageable carries the sort. */
    @EntityGraph(attributePaths = {"user", "role"})
    Page<Membership> findByOrganizationId(UUID organizationId, Pageable pageable);

    /** A membership only if it belongs to the organization, so another tenant's id reads as not found. */
    @EntityGraph(attributePaths = {"user", "role"})
    Optional<Membership> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** How many memberships in the organization hold this role. Used to keep at least one OWNER. */
    long countByOrganizationIdAndRoleName(UUID organizationId, String roleName);
}
