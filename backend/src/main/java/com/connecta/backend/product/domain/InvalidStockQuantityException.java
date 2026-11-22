package com.connecta.backend.product.domain;

/** A movement's quantity breaks the rule for its type, or the stock would exceed the largest integer. */
public class InvalidStockQuantityException extends RuntimeException {

    public InvalidStockQuantityException(String detail) {
        super(detail);
    }
}
