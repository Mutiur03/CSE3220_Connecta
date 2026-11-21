package com.connecta.backend.product.repository;

import com.connecta.backend.product.domain.ProductImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query takes the organization id, so another tenant's images are never returned. */
public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdAndOrganizationIdOrderByPositionAscIdAsc(UUID productId, UUID organizationId);

    Optional<ProductImage> findByIdAndProductIdAndOrganizationId(UUID id, UUID productId, UUID organizationId);

    long countByProductIdAndOrganizationId(UUID productId, UUID organizationId);

    /** The highest position in use, or -1 when the product has no image. */
    @Query("select coalesce(max(i.position), -1) from ProductImage i "
            + "where i.product.id = :productId and i.organization.id = :organizationId")
    int findMaxPosition(@Param("productId") UUID productId, @Param("organizationId") UUID organizationId);

    @Query("select i.objectKey from ProductImage i "
            + "where i.product.id = :productId and i.organization.id = :organizationId")
    List<String> findObjectKeys(@Param("productId") UUID productId, @Param("organizationId") UUID organizationId);
}
