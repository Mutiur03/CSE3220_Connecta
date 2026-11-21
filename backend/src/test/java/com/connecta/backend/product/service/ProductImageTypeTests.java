package com.connecta.backend.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProductImageTypeTests {

    @Test
    void detectsEachAllowedFormatFromItsMagicBytes() {
        assertEquals(ProductImageType.PNG, ProductImageType.detect(ProductImageSamples.PNG).orElseThrow());
        assertEquals(ProductImageType.JPEG, ProductImageType.detect(ProductImageSamples.JPEG).orElseThrow());
        assertEquals(ProductImageType.WEBP, ProductImageType.detect(ProductImageSamples.WEBP).orElseThrow());
    }

    @Test
    void anythingElseIsNotDetected() {
        assertTrue(ProductImageType.detect(ProductImageSamples.HTML).isEmpty());
        assertTrue(ProductImageType.detect(new byte[0]).isEmpty());
        assertTrue(ProductImageType.detect("GIF89a".getBytes()).isEmpty());
        assertTrue(ProductImageType.detect("RIFF\0\0\0\0WAVE".getBytes()).isEmpty());
    }

    @Test
    void mimeTypesMatchTheAllowedList() {
        assertEquals("image/jpeg", ProductImageType.JPEG.mimeType());
        assertEquals("image/png", ProductImageType.PNG.mimeType());
        assertEquals("image/webp", ProductImageType.WEBP.mimeType());
    }
}
