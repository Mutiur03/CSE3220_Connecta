package com.connecta.backend.auth.service;

/** No active session with that id belongs to the caller. Another user's session looks the same. */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException() {
        super("Session not found");
    }
}
