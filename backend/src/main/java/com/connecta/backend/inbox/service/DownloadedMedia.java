package com.connecta.backend.inbox.service;

/**
 * A downloaded file.
 *
 * @param bytes the content; never empty
 * @param contentType the type the host declared; may be null
 */
public record DownloadedMedia(byte[] bytes, String contentType) {
}
