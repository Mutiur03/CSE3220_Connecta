package com.connecta.backend.product.dto;

import java.math.BigDecimal;

/** Totals across the organization's products. stockValue is the sum of stock times unit price, 2 places. */
public record InventorySummaryResponse(
        long productCount,
        long totalUnits,
        BigDecimal stockValue,
        long lowStockCount,
        long outOfStockCount) {
}
