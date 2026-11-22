package com.connecta.backend.product.repository;

import com.connecta.backend.product.domain.ProductCategory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {

    /** A category only if it belongs to the organization, so another tenant's id reads as not found. */
    Optional<ProductCategory> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** One page of an organization's categories. The pageable carries the sort. */
    Page<ProductCategory> findByOrganizationId(UUID organizationId, Pageable pageable);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
