package com.connecta.backend.product.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.dto.CreateProductRequest;
import com.connecta.backend.product.dto.ProductResponse;
import com.connecta.backend.product.dto.UpdateProductRequest;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.product.repository.ProductSpecifications;
import com.connecta.backend.user.domain.Roles;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The product catalog of one organization. Any member reads; only an ADMIN of the organization writes.
 * Role checks go through {@link TenantAccessService}. Every product and category is loaded by id AND
 * organization, so another tenant's id reads as not found.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    /** Stable order for lists: by name, then by id so equal names never swap places. */
    private static final Sort BY_NAME = Sort.by("name").ascending().and(Sort.by("id"));

    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String subject, UUID organizationId, ProductFilter filter,
            int page, int size) {
        tenants.requireMembership(organizationId, callerId(subject));
        Specification<Product> spec = toSpecification(organizationId, filter);
        Page<Product> found = products.findAll(spec, PageRequest.of(page, size, BY_NAME));
        return PageResponse.from(found, ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(String subject, UUID organizationId, UUID productId) {
        tenants.requireMembership(organizationId, callerId(subject));
        return ProductResponse.from(findProduct(organizationId, productId));
    }

    /** ADMIN of the organization. */
    @Transactional
    public ProductResponse create(String subject, UUID organizationId, CreateProductRequest request) {
        Membership caller = tenants.requireRole(organizationId, callerId(subject), Roles.ADMIN);
        requireNameFree(organizationId, request.name());
        requireSkuFree(organizationId, request.sku());
        ProductCategory category = resolveCategory(organizationId, request.categoryId());
        Product product = new Product(caller.getOrganization(), category, request.name(), request.description(),
                request.sku(), request.unitPrice(), request.reorderLevel());
        return ProductResponse.from(products.save(product));
    }

    /** ADMIN of the organization. The name and SKU are checked against other products only when they change. */
    @Transactional
    public ProductResponse update(String subject, UUID organizationId, UUID productId, UpdateProductRequest request) {
        tenants.requireRole(organizationId, callerId(subject), Roles.ADMIN);
        Product product = findProduct(organizationId, productId);
        if (!product.getName().equals(request.name().trim())) {
            requireNameFree(organizationId, request.name());
        }
        if (request.sku() != null && !request.sku().equalsIgnoreCase(product.getSku())) {
            requireSkuFree(organizationId, request.sku());
        }
        product.update(request.name(), request.description(), request.sku(), request.unitPrice(),
                request.reorderLevel());
        product.changeCategory(resolveCategory(organizationId, request.categoryId()));
        return ProductResponse.from(product);
    }

    /** ADMIN of the organization. */
    @Transactional
    public void delete(String subject, UUID organizationId, UUID productId) {
        tenants.requireRole(organizationId, callerId(subject), Roles.ADMIN);
        Product product = findProduct(organizationId, productId);
        events.publishEvent(new ProductDeletedEvent(organizationId, productId));
        products.delete(product);
    }

    private UUID callerId(String subject) {
        return activeCaller.load(subject).getId();
    }

    private Product findProduct(UUID organizationId, UUID productId) {
        return products.findByIdAndOrganizationId(productId, organizationId)
                .orElseThrow(ProductNotFoundException::new);
    }

    private void requireNameFree(UUID organizationId, String name) {
        if (products.existsByOrganizationIdAndName(organizationId, name.trim())) {
            throw new ProductNameTakenException();
        }
    }

    private void requireSkuFree(UUID organizationId, String sku) {
        if (sku != null && products.existsByOrganizationIdAndSkuIgnoreCase(organizationId, sku)) {
            throw new ProductSkuTakenException();
        }
    }

    /** Filters combine with AND. A category of another organization is "not found", as for a missing one. */
    private Specification<Product> toSpecification(UUID organizationId, ProductFilter filter) {
        Specification<Product> spec = ProductSpecifications.inOrganization(organizationId);
        if (filter.categoryId() != null) {
            resolveCategory(organizationId, filter.categoryId());
            spec = spec.and(ProductSpecifications.inCategory(filter.categoryId()));
        }
        if (filter.text() != null && !filter.text().isBlank()) {
            spec = spec.and(ProductSpecifications.nameOrSkuContains(filter.text().trim()));
        }
        if (filter.stockStatus() != null) {
            spec = spec.and(ProductSpecifications.withStockStatus(filter.stockStatus()));
        }
        return spec;
    }

    /** Null means no category. A category of another organization is "not found", as for a missing one. */
    private ProductCategory resolveCategory(UUID organizationId, UUID categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categories.findByIdAndOrganizationId(categoryId, organizationId)
                .orElseThrow(ProductCategoryNotFoundException::new);
    }
}
