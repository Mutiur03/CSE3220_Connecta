package com.connecta.backend.inbox.service;

/** The conversation is already with the requested assignee (or already unassigned). */
public class AssignmentUnchangedException extends RuntimeException {

    public AssignmentUnchangedException() {
        super("The conversation is already assigned to this member");
    }
}
