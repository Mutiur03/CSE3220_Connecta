package com.connecta.backend.setup.service;

/** The system owner already exists, so the first-run setup is closed. */
public class SetupAlreadyCompleteException extends RuntimeException {

    public SetupAlreadyCompleteException() {
        super("Setup is already complete");
    }
}
