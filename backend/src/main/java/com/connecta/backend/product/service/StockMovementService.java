package com.connecta.backend.product.service;

import com.connecta.backend.common.dto.PageResponse;
import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.StockMovement;
import com.connecta.backend.product.dto.RecordStockMovementRequest;
import com.connecta.backend.product.dto.StockMovementResponse;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.product.repository.StockMovementRepository;
import com.connecta.backend.user.domain.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records stock movements and reads their history. Any member of the organization may read and record;
 * a non-member, or a product of another organization, is "not found". A movement locks its product row
 * (see {@link ProductRepository#findForUpdateByIdAndOrganizationId}) so concurrent issues cannot overdraw it.
 */
@Service
@RequiredArgsConstructor
public class StockMovementService {

    /** Newest first; ties broken by id so a page never shuffles. */
    private static final Sort NEWEST_FIRST = Sort.by("createdAt").descending().and(Sort.by("id"));

    private final ProductRepository products;
    private final StockMovementRepository movements;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;

    /** Applies the movement to the locked product and records it, in one transaction. */
    @Transactional
    public StockMovementResponse record(String subject, UUID organizationId, UUID productId,
            RecordStockMovementRequest request) {
        User caller = activeCaller.load(subject);
        tenants.requireMembership(organizationId, caller.getId());
        Product product = products.findForUpdateByIdAndOrganizationId(productId, organizationId)
                .orElseThrow(ProductNotFoundException::new);
        int change = product.applyMovement(request.type(), request.quantity());
        StockMovement movement = movements.save(new StockMovement(product, request.type(), change,
                product.getStockQuantity(), request.reason(), caller));
        return StockMovementResponse.from(movement);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> listForProduct(String subject, UUID organizationId, UUID productId,
            int page, int size) {
        tenants.requireMembership(organizationId, callerId(subject));
        requireProduct(organizationId, productId);
        return toPage(movements.findByOrganizationIdAndProductId(organizationId, productId, newestFirst(page, size)));
    }

    /** The organization's movements; a productId narrows them to one product of the organization. */
    @Transactional(readOnly = true)
    public PageResponse<StockMovementResponse> listForOrganization(String subject, UUID organizationId,
            UUID productId, int page, int size) {
        tenants.requireMembership(organizationId, callerId(subject));
        if (productId == null) {
            return toPage(movements.findByOrganizationId(organizationId, newestFirst(page, size)));
        }
        requireProduct(organizationId, productId);
        return toPage(movements.findByOrganizationIdAndProductId(organizationId, productId, newestFirst(page, size)));
    }

    @Transactional(readOnly = true)
    public StockMovementResponse get(String subject, UUID organizationId, UUID movementId) {
        tenants.requireMembership(organizationId, callerId(subject));
        return movements.findByIdAndOrganizationId(movementId, organizationId)
                .map(StockMovementResponse::from)
                .orElseThrow(StockMovementNotFoundException::new);
    }

    private UUID callerId(String subject) {
        return activeCaller.load(subject).getId();
    }

    private void requireProduct(UUID organizationId, UUID productId) {
        products.findByIdAndOrganizationId(productId, organizationId).orElseThrow(ProductNotFoundException::new);
    }

    private static Pageable newestFirst(int page, int size) {
        return PageRequest.of(page, size, NEWEST_FIRST);
    }

    private static PageResponse<StockMovementResponse> toPage(Page<StockMovement> found) {
        return PageResponse.from(found, StockMovementResponse::from);
    }
}
