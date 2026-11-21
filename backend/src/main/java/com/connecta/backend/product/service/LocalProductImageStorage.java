package com.connecta.backend.product.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ProductImageStorage} for dev and tests. Bytes are lost on restart and nothing serves the
 * {@code local://} URLs, so it is never the production store.
 */
public class LocalProductImageStorage implements ProductImageStorage {

    private static final String LOCAL_URL_PREFIX = "local://";

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String store(UUID organizationId, UUID productId, String contentType, byte[] content) {
        String key = ProductImageKeys.create(organizationId, productId, contentType);
        objects.put(key, content.clone());
        return key;
    }

    @Override
    public void delete(String key) {
        objects.remove(key);
    }

    @Override
    public String publicUrl(String key) {
        return LOCAL_URL_PREFIX + key;
    }

    @Override
    public Optional<byte[]> read(UUID organizationId, String key) {
        if (key == null || !key.startsWith(ProductImageKeys.organizationPrefix(organizationId))) {
            return Optional.empty();
        }
        return Optional.ofNullable(objects.get(key)).map(byte[]::clone);
    }
}
