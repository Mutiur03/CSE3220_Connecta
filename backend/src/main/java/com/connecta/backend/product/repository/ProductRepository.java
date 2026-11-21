package com.connecta.backend.product.repository;

import com.connecta.backend.product.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /** A product only if it belongs to the organization. Loads the category so callers can read it. */
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** One page of an organization's products. The pageable carries the sort. */
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByOrganizationId(UUID organizationId, Pageable pageable);

    /** One page of an organization's products in one category. The service checks the category is the organization's own. */
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByOrganizationIdAndCategoryId(UUID organizationId, UUID categoryId, Pageable pageable);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);
}
