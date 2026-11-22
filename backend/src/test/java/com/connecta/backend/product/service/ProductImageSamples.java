package com.connecta.backend.product.service;

import java.nio.charset.StandardCharsets;

/** Smallest byte sequences that carry each format's magic bytes, for tests. */
public final class ProductImageSamples {

    public static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    public static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};
    public static final byte[] WEBP = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    public static final byte[] HTML = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

    private ProductImageSamples() {
    }
}
