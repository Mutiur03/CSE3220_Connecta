package com.connecta.backend.meta.service;

/** A stored Meta token could not be decrypted: it was altered, or the encryption key changed. */
public class TokenDecryptionException extends RuntimeException {

    public TokenDecryptionException(Throwable cause) {
        super("Stored Meta token could not be decrypted", cause);
    }
}
