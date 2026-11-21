package com.connecta.backend.product.service;

/** No product with this id in the organization: unknown, or another organization's. */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException() {
        super("Product not found");
    }
}
