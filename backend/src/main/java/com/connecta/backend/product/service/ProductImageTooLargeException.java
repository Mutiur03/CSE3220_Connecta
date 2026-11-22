package com.connecta.backend.product.service;

/** The file is larger than the configured limit. */
public class ProductImageTooLargeException extends RuntimeException {

    public ProductImageTooLargeException() {
        super("Product image is too large");
    }
}
