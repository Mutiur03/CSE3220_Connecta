package com.connecta.backend.meta.service;

/** A webhook body that is not a JSON object. */
public class MalformedWebhookException extends RuntimeException {

    public MalformedWebhookException() {
        super("Webhook body is not valid JSON");
    }
}
