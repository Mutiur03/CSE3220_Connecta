package com.connecta.backend.meta.domain;

import java.util.Optional;

/**
 * The Meta product a webhook delivery belongs to, read from the payload's "object" field. Only these two are
 * stored; any other product (for example WhatsApp) is acknowledged and ignored.
 */
public enum WebhookObject {
    PAGE("page"),
    INSTAGRAM("instagram");

    private final String metaName;

    WebhookObject(String metaName) {
        this.metaName = metaName;
    }

    public static Optional<WebhookObject> fromMetaName(String metaName) {
        for (WebhookObject object : values()) {
            if (object.metaName.equals(metaName)) {
                return Optional.of(object);
            }
        }
        return Optional.empty();
    }
}
