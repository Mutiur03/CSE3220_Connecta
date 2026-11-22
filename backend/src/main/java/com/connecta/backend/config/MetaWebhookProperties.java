package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The verify token Meta sends back when it subscribes the webhook (app.meta.webhook.*), read from
 * META_WEBHOOK_VERIFY_TOKEN. Blank means no subscription is accepted: the GET check always refuses.
 */
@ConfigurationProperties(prefix = "app.meta.webhook")
public record MetaWebhookProperties(String verifyToken) {

    public boolean configured() {
        return verifyToken != null && !verifyToken.isBlank();
    }
}
