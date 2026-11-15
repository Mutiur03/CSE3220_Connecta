package com.connecta.backend.org.service;

/** The person is already a member of the organization. */
public class AlreadyMemberException extends RuntimeException {

    public AlreadyMemberException() {
        super("This user is already a member of the organization");
    }
}
