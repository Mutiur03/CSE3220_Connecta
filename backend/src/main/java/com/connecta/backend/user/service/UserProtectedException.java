package com.connecta.backend.user.service;

/** The account may not be disabled: it is the system OWNER, and disabling it would lock the system out. */
public class UserProtectedException extends RuntimeException {

    public UserProtectedException() {
        super("The system owner cannot be disabled");
    }
}
