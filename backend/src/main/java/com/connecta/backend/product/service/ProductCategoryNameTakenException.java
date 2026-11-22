package com.connecta.backend.product.service;

/** Another category in the same organization already has this name. */
public class ProductCategoryNameTakenException extends RuntimeException {

    public ProductCategoryNameTakenException() {
        super("A product category with this name already exists");
    }
}
