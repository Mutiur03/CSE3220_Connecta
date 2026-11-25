package com.connecta.backend.inbox.service;

/**
 * Adapter boundary to the outside media host (Meta's CDN): fetches the bytes behind an attachment's source URL.
 * The inbox never sees HTTP; tests use a fake.
 */
public interface MediaDownloader {

    /**
     * The file at the URL.
     *
     * @throws MediaDownloadException the URL is refused (scheme, host, address), the host answered with an error,
     *     the file is empty or too large, or the network failed
     */
    DownloadedMedia download(String url);
}
