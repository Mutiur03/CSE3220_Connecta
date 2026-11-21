package com.connecta.backend.product.service;

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Adapter from {@link ProductImageStorage} to any S3-compatible service (AWS S3, Cloudflare R2, MinIO, ...). The S3
 * client is built in {@code config.ProductImageStorageConfig}; this class only translates calls and SDK failures.
 */
@RequiredArgsConstructor
public class S3ProductImageStorage implements ProductImageStorage {

    private final S3Client s3;
    private final String bucket;
    /** Public base URL without a trailing slash, for example https://images.example.com. */
    private final String publicBaseUrl;

    @Override
    public String store(UUID organizationId, UUID productId, String contentType, byte[] content) {
        String key = ProductImageKeys.create(organizationId, productId, contentType);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        try {
            s3.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw new ProductImageStorageException(e);
        }
        return key;
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            s3.deleteObject(request);
        } catch (SdkException e) {
            throw new ProductImageStorageException(e);
        }
    }

    @Override
    public Optional<byte[]> read(UUID organizationId, String key) {
        if (key == null || !key.startsWith(ProductImageKeys.organizationPrefix(organizationId))) {
            return Optional.empty();
        }
        GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            return Optional.of(s3.getObjectAsBytes(request).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw new ProductImageStorageException(e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }
}
