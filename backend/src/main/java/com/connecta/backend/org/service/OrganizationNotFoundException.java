package com.connecta.backend.org.service;

import java.util.UUID;

/**
 * The organization does not exist or the caller is not a member. The two cases look the same to the
 * caller, so an organization's existence is not revealed to outsiders.
 */
public class OrganizationNotFoundException extends RuntimeException {

    public OrganizationNotFoundException(UUID organizationId) {
        super("Organization not found: " + organizationId);
    }
}
