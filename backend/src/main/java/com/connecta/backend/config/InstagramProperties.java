package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Instagram app for Business Login (app.meta.instagram.*), separate from the Facebook app: read from
 * META_INSTAGRAM_APP_ID and META_INSTAGRAM_APP_SECRET. Blank means the Instagram connection answers 503.
 */
@ConfigurationProperties(prefix = "app.meta.instagram")
public record InstagramProperties(String appId, String appSecret) {

    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
