package com.connecta.backend.meta.service;

/** The Meta app (app.meta.*) is not configured on this server, so Meta features are unavailable. */
public class MetaNotConfiguredException extends RuntimeException {

    public MetaNotConfiguredException() {
        super("Meta is not configured on this server");
    }
}
