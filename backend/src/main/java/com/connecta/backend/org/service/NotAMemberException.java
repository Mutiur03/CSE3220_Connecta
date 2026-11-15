package com.connecta.backend.org.service;

/**
 * The user has no membership in the organization, or the organization does not exist. Both cases carry
 * the same message, so the 404 answer never reveals whether another tenant exists.
 */
public class NotAMemberException extends RuntimeException {

    public NotAMemberException() {
        super("Organization not found");
    }
}
