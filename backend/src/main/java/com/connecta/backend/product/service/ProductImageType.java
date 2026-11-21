package com.connecta.backend.product.service;

import java.util.Arrays;
import java.util.Optional;

/** The image formats a product may store. The format is decided from the file's leading bytes, never from its name. */
public enum ProductImageType {
    JPEG("image/jpeg") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0xFF, 0xD8, 0xFF);
        }
    },
    PNG("image/png") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
        }
    },
    WEBP("image/webp") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P');
        }
    };

    private final String mimeType;

    ProductImageType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String mimeType() {
        return mimeType;
    }

    abstract boolean matches(byte[] content);

    /** The type whose magic bytes the content starts with, if any. */
    public static Optional<ProductImageType> detect(byte[] content) {
        return Arrays.stream(values()).filter(type -> type.matches(content)).findFirst();
    }

    private static boolean startsWith(byte[] content, int offset, int... expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
