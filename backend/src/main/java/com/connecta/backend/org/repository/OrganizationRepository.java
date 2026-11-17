package com.connecta.backend.org.repository;

import com.connecta.backend.org.domain.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Every organization with its member count in one query (no N+1). The pageable carries the sort. */
    @Query(value = "select o.id as id, o.name as name, o.slug as slug, o.createdAt as createdAt, count(m) as memberCount "
            + "from Organization o left join Membership m on m.organization = o "
            + "group by o.id, o.name, o.slug, o.createdAt",
            countQuery = "select count(o) from Organization o")
    Page<OrganizationSummary> findAllSummaries(Pageable pageable);
}
