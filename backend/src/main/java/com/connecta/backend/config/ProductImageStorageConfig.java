package com.connecta.backend.config;

import com.connecta.backend.product.service.LocalProductImageStorage;
import com.connecta.backend.product.service.ProductImageStorage;
import com.connecta.backend.product.service.R2ProductImageStorage;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Chooses the product image store once, at startup (factory method): R2 when configured, otherwise the in-memory
 * store. In prod (app.storage.r2.required) missing R2 settings fail startup instead.
 */
@Configuration
class ProductImageStorageConfig {

    private static final String R2_HOST_SUFFIX = ".r2.cloudflarestorage.com";
    /** R2 ignores regions; the S3 SDK still needs one. */
    private static final String R2_REGION = "auto";

    @Bean
    ProductImageStorage productImageStorage(R2Properties r2) {
        if (r2.configured()) {
            String publicBaseUrl = r2.publicBaseUrl().replaceAll("/+$", "");
            return new R2ProductImageStorage(s3Client(r2), r2.bucket(), publicBaseUrl);
        }
        if (r2.required()) {
            throw new IllegalStateException("Product image storage requires R2 but it is not configured: set "
                    + "R2_ACCOUNT_ID, R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY and R2_PUBLIC_BASE_URL");
        }
        return new LocalProductImageStorage();
    }

    private static S3Client s3Client(R2Properties r2) {
        return S3Client.builder()
                .endpointOverride(URI.create("https://" + r2.accountId() + R2_HOST_SUFFIX))
                .region(Region.of(R2_REGION))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.accessKeyId(), r2.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .build();
    }
}
