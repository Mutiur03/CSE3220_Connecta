package com.connecta.backend.auth.service;

/** Signup is closed until the first-run setup has created the system owner. */
public class SetupRequiredException extends RuntimeException {

    public SetupRequiredException() {
        super("Setup is required before accounts can be created");
    }
}
