package com.connecta.backend.inbox.service;

/** The ways new conversations can be handed to an agent. Only round-robin exists; it is the default. */
public enum AssignmentMode {
    ROUND_ROBIN;

    public static final AssignmentMode DEFAULT = ROUND_ROBIN;
}
