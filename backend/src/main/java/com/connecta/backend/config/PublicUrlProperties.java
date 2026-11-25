package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This backend's public address (app.public-base-url, from PUBLIC_BASE_URL), used to build absolute links that an
 * outside system such as Meta must fetch. Blank means not configured: features that need such a link refuse.
 */
@ConfigurationProperties(prefix = "app")
public record PublicUrlProperties(String publicBaseUrl) {

    /** The address without a trailing slash, or an empty string when not configured. */
    public String baseUrl() {
        return publicBaseUrl == null ? "" : publicBaseUrl.strip().replaceAll("/+$", "");
    }
}
