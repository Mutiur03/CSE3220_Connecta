package com.connecta.backend.product.domain;

/**
 * What a stock movement does. RECEIVE and ISSUE move stock by a quantity of at least 1; ADJUST sets the
 * stock to an absolute level, so 0 is a valid level.
 */
public enum StockMovementType {
    RECEIVE(1),
    ISSUE(1),
    ADJUST(0);

    private final int minimumQuantity;

    StockMovementType(int minimumQuantity) {
        this.minimumQuantity = minimumQuantity;
    }

    public int minimumQuantity() {
        return minimumQuantity;
    }
}
