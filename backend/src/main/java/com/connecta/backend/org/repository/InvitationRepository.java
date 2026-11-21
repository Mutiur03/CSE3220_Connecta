package com.connecta.backend.org.repository;

import com.connecta.backend.org.domain.Invitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /** Loads the organization, role and inviter, which accepting or previewing an invitation needs. */
    @EntityGraph(attributePaths = {"organization", "role", "invitedBy"})
    Optional<Invitation> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = {"role"})
    Optional<Invitation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** One page of an organization's invitations, of any status. The pageable carries the sort. */
    @EntityGraph(attributePaths = {"role"})
    Page<Invitation> findByOrganizationId(UUID organizationId, Pageable pageable);

    boolean existsByOrganizationIdAndEmailAndStatus(UUID organizationId, String email, Invitation.Status status);
}
