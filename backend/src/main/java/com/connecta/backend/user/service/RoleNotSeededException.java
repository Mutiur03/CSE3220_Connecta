package com.connecta.backend.user.service;

/** A role the app depends on is missing from the database. A server fault, never shown to clients. */
public class RoleNotSeededException extends RuntimeException {

    public RoleNotSeededException(String roleName) {
        super("Role " + roleName + " is not seeded");
    }
}
