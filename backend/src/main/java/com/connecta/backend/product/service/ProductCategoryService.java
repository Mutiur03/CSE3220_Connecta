package com.connecta.backend.product.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.domain.Membership;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.ProductCategory;
import com.connecta.backend.product.dto.ProductCategoryRequest;
import com.connecta.backend.product.dto.ProductCategoryResponse;
import com.connecta.backend.product.repository.ProductCategoryRepository;
import com.connecta.backend.user.domain.Roles;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product category CRUD inside one organization. Reads need membership of that organization; writes need
 * ADMIN of it. Every lookup is scoped by organization, so another tenant's category id reads as not found.
 * Deleting a category does not touch its products: the database clears their category_id only.
 */
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    /** Stable order for lists: by name, then by id so equal names never swap places. */
    private static final Sort BY_NAME = Sort.by("name").ascending().and(Sort.by("id"));

    private final ProductCategoryRepository categories;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;

    /** Any member of the organization. */
    @Transactional(readOnly = true)
    public PageResponse<ProductCategoryResponse> list(String subject, UUID organizationId, int page, int size) {
        requireMember(subject, organizationId);
        Page<ProductCategory> found =
                categories.findByOrganizationId(organizationId, PageRequest.of(page, size, BY_NAME));
        return PageResponse.from(found, ProductCategoryResponse::from);
    }

    /** Any member of the organization. */
    @Transactional(readOnly = true)
    public ProductCategoryResponse get(String subject, UUID organizationId, UUID categoryId) {
        requireMember(subject, organizationId);
        return ProductCategoryResponse.from(findCategory(organizationId, categoryId));
    }

    /** ADMIN of the organization. The name must be unused inside it. */
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Transactional
    public ProductCategoryResponse create(String subject, UUID organizationId, ProductCategoryRequest request) {
        Membership admin = requireAdmin(subject, organizationId);
        String name = request.name().trim();
        requireNameFree(organizationId, name);
        try {
            ProductCategory created = categories.saveAndFlush(
                    new ProductCategory(admin.getOrganization(), name, request.description()));
            return ProductCategoryResponse.from(created);
        } catch (DataIntegrityViolationException e) {
            // A concurrent create with the same name surfaces here, as a 409, and not at commit.
            throw new ProductCategoryNameTakenException();
        }
    }

    /** ADMIN of the organization. A new name must be unused inside it; keeping the same name is always fine. */
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Transactional
    public ProductCategoryResponse update(String subject, UUID organizationId, UUID categoryId,
            ProductCategoryRequest request) {
        requireAdmin(subject, organizationId);
        ProductCategory category = findCategory(organizationId, categoryId);
        String name = request.name().trim();
        if (!name.equals(category.getName())) {
            requireNameFree(organizationId, name);
        }
        category.update(name, request.description());
        try {
            return ProductCategoryResponse.from(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException e) {
            throw new ProductCategoryNameTakenException();
        }
    }

    /** ADMIN of the organization. Products in the category stay, uncategorized. */
    @PreAuthorize("hasRole('" + Roles.ADMIN + "')")
    @Transactional
    public void delete(String subject, UUID organizationId, UUID categoryId) {
        requireAdmin(subject, organizationId);
        categories.delete(findCategory(organizationId, categoryId));
    }

    private void requireMember(String subject, UUID organizationId) {
        tenants.requireMembership(organizationId, activeCaller.load(subject).getId());
    }

    private Membership requireAdmin(String subject, UUID organizationId) {
        return tenants.requireRole(organizationId, activeCaller.load(subject).getId(), Roles.ADMIN);
    }

    private ProductCategory findCategory(UUID organizationId, UUID categoryId) {
        return categories.findByIdAndOrganizationId(categoryId, organizationId)
                .orElseThrow(ProductCategoryNotFoundException::new);
    }

    private void requireNameFree(UUID organizationId, String name) {
        if (categories.existsByOrganizationIdAndName(organizationId, name)) {
            throw new ProductCategoryNameTakenException();
        }
    }
}
