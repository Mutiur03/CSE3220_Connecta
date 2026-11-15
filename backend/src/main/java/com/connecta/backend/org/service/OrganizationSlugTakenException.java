package com.connecta.backend.org.service;

public class OrganizationSlugTakenException extends RuntimeException {

    public OrganizationSlugTakenException() {
        super("An organization with this slug already exists");
    }
}
