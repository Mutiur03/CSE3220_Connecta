package com.connecta.backend.product.service;

/** Another product in the same organization already has this name. */
public class ProductNameTakenException extends RuntimeException {

    public ProductNameTakenException() {
        super("A product with this name already exists");
    }
}
