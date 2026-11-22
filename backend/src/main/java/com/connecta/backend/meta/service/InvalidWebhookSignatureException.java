package com.connecta.backend.meta.service;

/** The webhook's X-Hub-Signature-256 is missing, malformed or does not match the body. */
public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException() {
        super("Invalid webhook signature");
    }
}
