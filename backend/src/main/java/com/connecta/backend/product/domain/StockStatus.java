package com.connecta.backend.product.domain;

/** How much of a product is left. The single place that holds the rule. */
public enum StockStatus {
    OUT_OF_STOCK,
    LOW_STOCK,
    IN_STOCK;

    /** Out at zero; low when something is left but at or below a reorder level that is set (above 0). */
    public static StockStatus of(int stockQuantity, int reorderLevel) {
        if (stockQuantity <= 0) {
            return OUT_OF_STOCK;
        }
        return reorderLevel > 0 && stockQuantity <= reorderLevel ? LOW_STOCK : IN_STOCK;
    }
}
