package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Product image storage on any S3-compatible service (app.storage.s3.*): AWS S3, Cloudflare R2, MinIO, Backblaze B2,
 * Wasabi, DigitalOcean Spaces and similar. Read from S3_ENDPOINT, S3_REGION, S3_BUCKET, S3_ACCESS_KEY_ID,
 * S3_SECRET_ACCESS_KEY, S3_PATH_STYLE_ACCESS and S3_PUBLIC_BASE_URL. The endpoint is optional (blank means the AWS
 * default). S3 is used only when bucket, both keys and the public base URL are set. required is true in prod:
 * there, missing settings stop startup instead of falling back to the in-memory store.
 */
@ConfigurationProperties(prefix = "app.storage.s3")
public record S3StorageProperties(String endpoint, String region, String bucket, String accessKeyId,
        String secretAccessKey, boolean pathStyleAccess, String publicBaseUrl, boolean required) {

    static final String DEFAULT_REGION = "us-east-1";

    public S3StorageProperties {
        region = notBlank(region) ? region : DEFAULT_REGION;
    }

    public boolean configured() {
        return notBlank(bucket) && notBlank(accessKeyId) && notBlank(secretAccessKey) && notBlank(publicBaseUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
