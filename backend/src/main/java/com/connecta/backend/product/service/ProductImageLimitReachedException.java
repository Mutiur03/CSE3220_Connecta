package com.connecta.backend.product.service;

/** The product already has the maximum number of images. */
public class ProductImageLimitReachedException extends RuntimeException {

    public ProductImageLimitReachedException() {
        super("Product image limit reached");
    }
}
