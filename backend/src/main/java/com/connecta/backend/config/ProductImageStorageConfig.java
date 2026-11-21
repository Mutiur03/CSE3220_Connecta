package com.connecta.backend.config;

import com.connecta.backend.product.service.LocalProductImageStorage;
import com.connecta.backend.product.service.ProductImageStorage;
import com.connecta.backend.product.service.S3ProductImageStorage;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Chooses the product image store once, at startup (factory method): S3 when configured, otherwise the in-memory
 * store. In prod (app.storage.s3.required) missing S3 settings fail startup instead.
 */
@Configuration
class ProductImageStorageConfig {

    @Bean
    ProductImageStorage productImageStorage(S3StorageProperties s3) {
        if (s3.configured()) {
            String publicBaseUrl = s3.publicBaseUrl().replaceAll("/+$", "");
            return new S3ProductImageStorage(s3Client(s3), s3.bucket(), publicBaseUrl);
        }
        if (s3.required()) {
            throw new IllegalStateException("Product image storage requires S3 but it is not configured: set "
                    + "S3_BUCKET, S3_ACCESS_KEY_ID, S3_SECRET_ACCESS_KEY and S3_PUBLIC_BASE_URL");
        }
        return new LocalProductImageStorage();
    }

    static S3Client s3Client(S3StorageProperties s3) {
        return configure(S3Client.builder(), s3)
                .build();
    }

    /**
     * Applies the connection settings. The endpoint is overridden only when S3_ENDPOINT is set, so AWS keeps its
     * default; path-style addressing is set from S3_PATH_STYLE_ACCESS (MinIO needs it).
     */
    static S3ClientBuilder configure(S3ClientBuilder builder, S3StorageProperties s3) {
        builder.region(Region.of(s3.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3.accessKeyId(), s3.secretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(s3.pathStyleAccess())
                        .build())
                // Some providers reject the checksum trailers the SDK adds by default; send them only when required.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
        if (hasEndpoint(s3)) {
            builder.endpointOverride(URI.create(s3.endpoint().strip()));
        }
        return builder;
    }

    private static boolean hasEndpoint(S3StorageProperties s3) {
        return s3.endpoint() != null && !s3.endpoint().isBlank();
    }
}
