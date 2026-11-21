package com.connecta.backend.product.service;

/** The image does not exist for this product, or belongs to another organization. */
public class ProductImageNotFoundException extends RuntimeException {

    public ProductImageNotFoundException() {
        super("Product image not found");
    }
}
