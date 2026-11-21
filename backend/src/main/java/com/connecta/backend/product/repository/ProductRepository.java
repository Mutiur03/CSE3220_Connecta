package com.connecta.backend.product.repository;

import com.connecta.backend.product.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    /** A product only if it belongs to the organization. Loads the category so callers can read it. */
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * A product of the organization, locked for the rest of the transaction (SELECT ... FOR UPDATE). Stock
     * movements use it so two issues on one product run one after another and never overdraw the stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findForUpdateByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Stock totals of the organization, summed in the database. Null sums mean no products. */
    @Query("select new com.connecta.backend.product.repository.InventoryTotals("
            + "sum(p.stockQuantity), sum(p.stockQuantity * p.unitPrice)) "
            + "from Product p where p.organization.id = :organizationId")
    InventoryTotals sumStock(@Param("organizationId") UUID organizationId);

    /** One page of products matching the filters (see {@link ProductSpecifications}), category fetched in the same query. */
    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    boolean existsByOrganizationIdAndName(UUID organizationId, String name);

    boolean existsByOrganizationIdAndSkuIgnoreCase(UUID organizationId, String sku);
}
