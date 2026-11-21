package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Product image storage on Cloudflare R2 (app.storage.r2.*), read from R2_ACCOUNT_ID, R2_BUCKET, R2_ACCESS_KEY_ID,
 * R2_SECRET_ACCESS_KEY, R2_PUBLIC_BASE_URL. R2 is used only when all five are set. required is true in prod:
 * there, missing R2 settings stop startup instead of falling back to the in-memory store.
 */
@ConfigurationProperties(prefix = "app.storage.r2")
public record R2Properties(String accountId, String bucket, String accessKeyId, String secretAccessKey,
        String publicBaseUrl, boolean required) {

    public boolean configured() {
        return notBlank(accountId) && notBlank(bucket) && notBlank(accessKeyId)
                && notBlank(secretAccessKey) && notBlank(publicBaseUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
