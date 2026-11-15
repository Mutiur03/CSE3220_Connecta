package com.connecta.backend.org.service;

/** The user is a member of the organization but holds a lower role than the action requires. */
public class InsufficientOrganizationRoleException extends RuntimeException {

    public InsufficientOrganizationRoleException() {
        super("Your role in this organization does not allow this action");
    }
}
