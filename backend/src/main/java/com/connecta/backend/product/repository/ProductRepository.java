package com.connecta.backend.product.repository;

import com.connecta.backend.product.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /** A product only if it belongs to the organization. Loads the category so callers can read it. */
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** One page of products matching the filters (see {@link ProductSpecifications}), category fetched in the same query. */
    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);

    boolean existsByOrganizationIdAndSkuIgnoreCase(UUID organizationId, String sku);
}
