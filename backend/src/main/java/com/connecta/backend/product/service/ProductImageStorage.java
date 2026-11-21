package com.connecta.backend.product.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Keeps product image bytes outside the database. Two variants: an S3-compatible store (production) and an
 * in-memory store (dev and tests). The keys are tenant-scoped, see {@link ProductImageKeys}.
 */
public interface ProductImageStorage {

    /** Stores the bytes under a generated key and returns that key. The client filename is never used. */
    String store(UUID organizationId, UUID productId, String contentType, byte[] content);

    /** Removes the object. Deleting a key that is already gone is not an error. */
    void delete(String key);

    /**
     * Loads the bytes of a key. Empty when the object is gone or the key does not belong to the organization, so
     * another tenant's key never reads.
     */
    Optional<byte[]> read(UUID organizationId, String key);

    /** The address the object can be loaded from. */
    String publicUrl(String key);
}
