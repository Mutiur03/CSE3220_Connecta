package com.connecta.backend.inbox.service;

/** The chosen member belongs to the organization but cannot take conversations: the account is disabled. */
public class AssigneeNotAvailableException extends RuntimeException {

    public AssigneeNotAvailableException() {
        super("This member is disabled and cannot take conversations");
    }
}
