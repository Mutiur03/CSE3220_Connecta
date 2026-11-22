package com.connecta.backend.product.service;

import com.connecta.backend.config.ProductImageProperties;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductImage;
import com.connecta.backend.product.dto.ProductImageResponse;
import com.connecta.backend.product.repository.ProductImageRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.user.domain.Roles;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Images of a product. Any member reads; only an ADMIN of the organization uploads or deletes. Products and images
 * are loaded by id AND organization, so another tenant's id reads as not found. Bytes go through
 * {@link ProductImageStorage}; this service never knows which store is behind it.
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository images;
    private final ProductRepository products;
    private final ProductImageStorage storage;
    private final ProductImageProperties limits;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;

    /**
     * ADMIN of the organization. The product row is locked first, so concurrent uploads to one product run one after
     * another and the count sees every committed image. Then size, real type (magic bytes must match the declared
     * type), and the count.
     */
    @Transactional
    public ProductImageResponse upload(String subject, UUID organizationId, UUID productId, String declaredType,
            byte[] content) {
        tenants.requireRole(organizationId, callerId(subject), Roles.ADMIN);
        Product product = lockProduct(organizationId, productId);
        if (content.length > limits.maxSizeBytes()) {
            throw new ProductImageTooLargeException();
        }
        ProductImageType type = requireMatchingType(declaredType, content);
        if (images.countByProductIdAndOrganizationId(productId, organizationId) >= limits.maxPerProduct()) {
            throw new ProductImageLimitReachedException();
        }
        int position = images.findMaxPosition(productId, organizationId) + 1;
        String key = storage.store(organizationId, productId, type.mimeType(), content);
        return ProductImageResponse.from(saveOrRemoveObject(new ProductImage(product, key, type.mimeType(),
                content.length, position)));
    }

    @Transactional(readOnly = true)
    public List<ProductImageResponse> list(String subject, UUID organizationId, UUID productId) {
        tenants.requireMembership(organizationId, callerId(subject));
        findProduct(organizationId, productId);
        return images.findByProductIdAndOrganizationIdOrderByPositionAscIdAsc(productId, organizationId).stream()
                .map(ProductImageResponse::from)
                .toList();
    }

    /** Any member. A row whose object is missing from the store is also not found. */
    @Transactional(readOnly = true)
    public ProductImageContent content(String subject, UUID organizationId, UUID productId, UUID imageId) {
        tenants.requireMembership(organizationId, callerId(subject));
        ProductImage image = findImage(organizationId, productId, imageId);
        byte[] bytes = storage.read(organizationId, image.getObjectKey())
                .orElseThrow(ProductImageNotFoundException::new);
        return new ProductImageContent(image.getContentType(), bytes);
    }

    /** ADMIN of the organization. The object goes first: a storage failure rolls the row delete back. */
    @Transactional
    public void delete(String subject, UUID organizationId, UUID productId, UUID imageId) {
        tenants.requireRole(organizationId, callerId(subject), Roles.ADMIN);
        ProductImage image = findImage(organizationId, productId, imageId);
        storage.delete(image.getObjectKey());
        images.delete(image);
    }

    private ProductImageType requireMatchingType(String declaredType, byte[] content) {
        String declared = declaredType == null ? "" : declaredType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        return ProductImageType.detect(content)
                .filter(type -> type.mimeType().equals(declared))
                .orElseThrow(UnsupportedProductImageTypeException::new);
    }

    /** A row that cannot be saved must not leave an unreachable object behind. */
    private ProductImage saveOrRemoveObject(ProductImage image) {
        try {
            return images.save(image);
        } catch (RuntimeException e) {
            storage.delete(image.getObjectKey());
            throw e;
        }
    }

    private UUID callerId(String subject) {
        return activeCaller.load(subject).getId();
    }

    private Product lockProduct(UUID organizationId, UUID productId) {
        return products.findForUpdateByIdAndOrganizationId(productId, organizationId)
                .orElseThrow(ProductNotFoundException::new);
    }

    private Product findProduct(UUID organizationId, UUID productId) {
        return products.findByIdAndOrganizationId(productId, organizationId)
                .orElseThrow(ProductNotFoundException::new);
    }

    private ProductImage findImage(UUID organizationId, UUID productId, UUID imageId) {
        return images.findByIdAndProductIdAndOrganizationId(imageId, productId, organizationId)
                .orElseThrow(ProductImageNotFoundException::new);
    }
}
