package com.connecta.backend.inbox.service;

/** No strategy is registered for the requested assignment mode (a wiring mistake, not a user error). */
public class AssignmentStrategyNotFoundException extends RuntimeException {

    public AssignmentStrategyNotFoundException(AssignmentMode mode) {
        super("No assignment strategy for mode " + mode);
    }
}
