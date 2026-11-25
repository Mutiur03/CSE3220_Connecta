package com.connecta.backend.inbox.service;

/**
 * One file an agent uploaded to send, as the controller received it.
 *
 * @param bytes the content; never null
 * @param contentType the type the browser declared; may be null
 * @param fileName the original file name; may be null
 */
public record AttachmentUpload(byte[] bytes, String contentType, String fileName) {
}
