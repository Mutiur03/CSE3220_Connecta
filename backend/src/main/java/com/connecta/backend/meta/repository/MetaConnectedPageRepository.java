package com.connecta.backend.meta.repository;

import com.connecta.backend.meta.domain.MetaConnectedPage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetaConnectedPageRepository extends JpaRepository<MetaConnectedPage, UUID> {

    /** One organization's connected Pages. Scoped by organization, so no other tenant's Page is returned. */
    Page<MetaConnectedPage> findByOrganizationId(UUID organizationId, Pageable pageable);

    Optional<MetaConnectedPage> findByOrganizationIdAndPageId(UUID organizationId, String pageId);
}
