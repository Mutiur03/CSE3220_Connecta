package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaConnectedAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaConnectedAccountRepository extends JpaRepository<MetaConnectedAccount, UUID> {

    /** Every lookup is scoped to the organization, so another tenant's account is never returned. */
    Page<MetaConnectedAccount> findByOrganizationIdAndPlatform(UUID organizationId, String platform, Pageable pageable);

    Optional<MetaConnectedAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<MetaConnectedAccount> findByOrganizationIdAndPlatformAndExternalAccountId(
            UUID organizationId, String platform, String externalAccountId);

    /**
     * Every organization that connected this account. Used only to route a webhook event to its tenants, so it is
     * the one lookup that is not scoped by organization.
     */
    List<MetaConnectedAccount> findAllByPlatformAndExternalAccountId(String platform, String externalAccountId);
}
