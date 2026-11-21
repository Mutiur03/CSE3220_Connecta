package com.connecta.backend.product.service;

/** The content type is not one of the image types a product may store (JPEG, PNG, WebP, GIF). */
public class UnsupportedProductImageTypeException extends RuntimeException {

    public UnsupportedProductImageTypeException() {
        super("Unsupported product image type");
    }
}
