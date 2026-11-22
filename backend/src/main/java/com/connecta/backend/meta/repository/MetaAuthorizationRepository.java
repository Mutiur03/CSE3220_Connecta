package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaAuthorization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaAuthorizationRepository extends JpaRepository<MetaAuthorization, UUID> {

    /** The organization's grant. Every lookup is scoped to the organization, so no other tenant's grant is returned. */
    Optional<MetaAuthorization> findByOrganizationId(UUID organizationId);
}
