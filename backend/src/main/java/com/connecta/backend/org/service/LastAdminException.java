package com.connecta.backend.org.service;

/** The organization must keep at least one ADMIN, so that member cannot be demoted or removed. */
public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("An organization must keep at least one ADMIN");
    }
}
