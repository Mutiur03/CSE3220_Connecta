package com.connecta.backend.product.service;

/** No category with this id in the organization: unknown, or another organization's. */
public class ProductCategoryNotFoundException extends RuntimeException {

    public ProductCategoryNotFoundException() {
        super("Category not found");
    }
}
