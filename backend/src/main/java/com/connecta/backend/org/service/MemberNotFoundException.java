package com.connecta.backend.org.service;

/** No membership with this id in the organization: unknown, or another organization's. */
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException() {
        super("Member not found");
    }
}
