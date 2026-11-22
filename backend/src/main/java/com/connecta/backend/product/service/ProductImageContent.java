package com.connecta.backend.product.service;

/** The bytes of one stored image and the content type it was stored with. */
public record ProductImageContent(String contentType, byte[] bytes) {
}
