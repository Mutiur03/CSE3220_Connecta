package com.connecta.backend.meta.service;

/** A requested Page is not part of the organization's Meta authorization (or there is none yet). */
public class MetaPageNotAllowedException extends RuntimeException {

    public MetaPageNotAllowedException() {
        super("One or more pages are not part of the Meta authorization");
    }
}
