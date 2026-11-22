package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where the browser goes after a Meta callback (app.frontend.*), read from FRONTEND_URL. The callbacks send the
 * admin back to the Integrations page with the result, instead of leaving them on a JSON response.
 */
@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String url) {

    /** The public web address without a trailing slash. */
    public String baseUrl() {
        return url == null ? "" : url.replaceAll("/+$", "");
    }
}
