package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Meta app used for the Facebook Login for Business connection (app.meta.*). Read from META_APP_ID,
 * META_APP_SECRET, META_GRAPH_API_VERSION, META_MESSENGER_CONFIG_ID, APP_BASE_URL and META_TOKEN_KEY.
 * The app settings may be blank (the Meta features then answer 503). tokenEncryptionKey is always required:
 * a 32-byte AES key, base64 encoded, used to encrypt stored Meta tokens.
 */
@ConfigurationProperties(prefix = "app.meta")
public record MetaProperties(String appId, String appSecret, String graphVersion, String configId,
        String baseUrl, String tokenEncryptionKey) {

    static final String DEFAULT_GRAPH_VERSION = "v27.0";

    public MetaProperties {
        graphVersion = notBlank(graphVersion) ? graphVersion : DEFAULT_GRAPH_VERSION;
    }

    /** True when the app, its login configuration and the public base URL are all set. */
    public boolean configured() {
        return notBlank(appId) && notBlank(appSecret) && notBlank(configId) && notBlank(baseUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
