package com.connecta.backend.product.repository;

import com.connecta.backend.product.domain.StockMovement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    /** A movement only if it belongs to the organization. Loads the product and the user who made it. */
    @EntityGraph(attributePaths = {"product", "createdBy"})
    Optional<StockMovement> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** The organization's movements, one page at a time. */
    @EntityGraph(attributePaths = {"product", "createdBy"})
    Page<StockMovement> findByOrganizationId(UUID organizationId, Pageable pageable);

    /** One product's movements inside the organization. */
    @EntityGraph(attributePaths = {"product", "createdBy"})
    Page<StockMovement> findByOrganizationIdAndProductId(UUID organizationId, UUID productId, Pageable pageable);
}
