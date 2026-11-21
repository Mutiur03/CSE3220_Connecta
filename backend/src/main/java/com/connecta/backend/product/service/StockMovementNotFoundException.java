package com.connecta.backend.product.service;

/** No stock movement with this id in the organization: unknown, or another organization's. */
public class StockMovementNotFoundException extends RuntimeException {

    public StockMovementNotFoundException() {
        super("Stock movement not found");
    }
}
