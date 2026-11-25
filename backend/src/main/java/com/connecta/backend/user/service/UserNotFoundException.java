package com.connecta.backend.user.service;

import java.util.UUID;

/** No user has this id. Answered with 404 by the user feature exception handler. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID userId) {
        super("User " + userId + " not found");
    }
}
