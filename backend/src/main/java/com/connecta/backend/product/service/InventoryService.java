package com.connecta.backend.product.service;

import com.connecta.backend.org.service.ActiveCaller;
import com.connecta.backend.org.service.TenantAccessService;
import com.connecta.backend.product.domain.Product;
import com.connecta.backend.product.domain.StockStatus;
import com.connecta.backend.product.dto.InventorySummaryResponse;
import com.connecta.backend.product.repository.InventoryTotals;
import com.connecta.backend.product.repository.ProductRepository;
import com.connecta.backend.product.repository.ProductSpecifications;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inventory totals for one organization. Any member reads; the counts reuse the StockStatus filters. */
@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final int MONEY_SCALE = 2;

    private final ProductRepository products;
    private final TenantAccessService tenants;
    private final ActiveCaller activeCaller;

    @Transactional(readOnly = true)
    public InventorySummaryResponse summary(String subject, UUID organizationId) {
        tenants.requireMembership(organizationId, activeCaller.load(subject).getId());
        Specification<Product> inOrganization = ProductSpecifications.inOrganization(organizationId);
        InventoryTotals totals = products.sumStock(organizationId);
        return new InventorySummaryResponse(
                products.count(inOrganization),
                orZero(totals.totalUnits()),
                stockValue(totals.stockValue()),
                countWithStatus(inOrganization, StockStatus.LOW_STOCK),
                countWithStatus(inOrganization, StockStatus.OUT_OF_STOCK));
    }

    private long countWithStatus(Specification<Product> inOrganization, StockStatus status) {
        return products.count(inOrganization.and(ProductSpecifications.withStockStatus(status)));
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    private static BigDecimal stockValue(BigDecimal value) {
        BigDecimal total = value == null ? BigDecimal.ZERO : value;
        return total.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
