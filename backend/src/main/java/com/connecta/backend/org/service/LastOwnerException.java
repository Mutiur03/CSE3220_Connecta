package com.connecta.backend.org.service;

/** The organization must keep at least one OWNER, so that member cannot be demoted or removed. */
public class LastOwnerException extends RuntimeException {

    public LastOwnerException() {
        super("An organization must keep at least one OWNER");
    }
}
