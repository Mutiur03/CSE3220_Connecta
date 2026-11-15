package com.connecta.backend.user.domain;

/** Role names; these rows are seeded by the users_and_roles migration. */
public final class Roles {

    public static final String OWNER = "OWNER";
    public static final String ADMIN = "ADMIN";
    public static final String AGENT = "AGENT";

    /** Prefix Spring Security puts before a role name in an authority. */
    public static final String AUTHORITY_PREFIX = "ROLE_";

    private Roles() {
    }
}
