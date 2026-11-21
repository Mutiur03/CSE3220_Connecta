package com.connecta.backend.product.domain;

/** An ISSUE asks for more units than the product has in stock. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException() {
        super("Not enough stock to issue this quantity");
    }
}
