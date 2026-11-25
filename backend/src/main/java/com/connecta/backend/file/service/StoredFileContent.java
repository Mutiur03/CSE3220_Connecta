package com.connecta.backend.file.service;

/** A stored file as the public read serves it. {@code fileName} is null when none was given. */
public record StoredFileContent(String contentType, String fileName, byte[] bytes) {
}
