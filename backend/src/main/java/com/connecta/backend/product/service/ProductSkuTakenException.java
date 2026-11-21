package com.connecta.backend.product.service;

/** Another product in the same organization already has this SKU (ignoring case). */
public class ProductSkuTakenException extends RuntimeException {

    public ProductSkuTakenException() {
        super("A product with this SKU already exists");
    }
}
