package com.connecta.backend.product.repository;

import java.math.BigDecimal;

/** Sums over an organization's products, as the database returns them. Null when there are no products. */
public record InventoryTotals(Long totalUnits, BigDecimal stockValue) {
}
