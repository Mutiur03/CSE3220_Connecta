package com.connecta.backend.meta.service;

/** Meta's subscription check failed: wrong mode, wrong or missing verify token, or no verify token configured. */
public class WebhookVerificationFailedException extends RuntimeException {

    public WebhookVerificationFailedException() {
        super("Webhook verification failed");
    }
}
