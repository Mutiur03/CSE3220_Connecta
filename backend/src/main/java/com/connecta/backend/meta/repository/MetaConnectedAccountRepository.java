package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaConnectedAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaConnectedAccountRepository extends JpaRepository<MetaConnectedAccount, UUID> {

    /** Every lookup is scoped to the organization, so another tenant's account is never returned. */
    Page<MetaConnectedAccount> findByOrganizationIdAndPlatform(UUID organizationId, String platform, Pageable pageable);

    Optional<MetaConnectedAccount> findByOrganizationIdAndPlatformAndExternalAccountId(
            UUID organizationId, String platform, String externalAccountId);
}
