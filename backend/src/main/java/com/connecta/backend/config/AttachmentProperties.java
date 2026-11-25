package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upload limit for message attachments (app.attachments.max-bytes). A missing or non-positive value falls back to
 * the default. Keep spring.servlet.multipart.max-file-size at or above it.
 */
@ConfigurationProperties(prefix = "app.attachments")
public record AttachmentProperties(long maxBytes) {

    static final long DEFAULT_MAX_BYTES = 8L * 1024 * 1024;

    public AttachmentProperties {
        maxBytes = maxBytes > 0 ? maxBytes : DEFAULT_MAX_BYTES;
    }
}
