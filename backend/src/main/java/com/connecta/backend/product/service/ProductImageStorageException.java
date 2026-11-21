package com.connecta.backend.product.service;

/**
 * The object store rejected or failed a request. The message is generic; the SDK cause is kept for the server log
 * only and never goes into a response.
 */
public class ProductImageStorageException extends RuntimeException {

    public ProductImageStorageException(Throwable cause) {
        super("Product image storage failed", cause);
    }
}
