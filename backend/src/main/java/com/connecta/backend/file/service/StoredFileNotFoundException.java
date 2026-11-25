package com.connecta.backend.file.service;

/** No stored file has the requested id. */
public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException() {
        super("File not found");
    }
}
