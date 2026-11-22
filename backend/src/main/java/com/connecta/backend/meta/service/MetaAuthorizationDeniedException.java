package com.connecta.backend.meta.service;

/** The admin cancelled the Meta login, so the callback came back without a code. */
public class MetaAuthorizationDeniedException extends RuntimeException {

    public MetaAuthorizationDeniedException() {
        super("The Meta login was cancelled");
    }
}
