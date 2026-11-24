package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaConnectedPage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaConnectedPageRepository extends JpaRepository<MetaConnectedPage, UUID> {

    /** One organization's connected Pages. Scoped by organization, so no other tenant's Page is returned. */
    Page<MetaConnectedPage> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<MetaConnectedPage> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<MetaConnectedPage> findByOrganizationIdAndPageId(UUID organizationId, String pageId);

    /**
     * Every organization that connected this Page. Used only to route a webhook event to its tenants, so it is
     * the one lookup that is not scoped by organization.
     */
    List<MetaConnectedPage> findAllByPageId(String pageId);
}
