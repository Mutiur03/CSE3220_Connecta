package com.connecta.backend.meta.service;

import com.connecta.backend.inbox.domain.AttachmentKind;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * One attachment of a {@link NormalizedMessage}: what it is and the URL Meta serves it from.
 *
 * @param kind image, video, audio, any other file, or a link to a shared post or story
 * @param url an absolute http(s) URL
 * @param fileName the file name when Meta gives one, the title of a link, else null
 * @param contentType the MIME type when Meta gives one, else null
 */
public record NormalizedAttachment(AttachmentKind kind, String url, String fileName, String contentType) {

    /** The title of a link to a post or reel the customer shared. */
    public static final String SHARED_POST = "Shared post";

    /** The attachment, or empty when the URL is missing or not an absolute http(s) URL. */
    public static Optional<NormalizedAttachment> of(AttachmentKind kind, String url, String fileName,
            String contentType) {
        if (!isWebUrl(url)) {
            return Optional.empty();
        }
        return Optional.of(new NormalizedAttachment(kind, url, blankToNull(fileName), blankToNull(contentType)));
    }

    /** A link to a shared post or story: the title is its file name, and there is no content type. */
    public static Optional<NormalizedAttachment> link(String url, String title) {
        return of(AttachmentKind.LINK, url, title, null);
    }

    /** The message text when this attachment arrives without text: a link's title in brackets, else the kind's. */
    public String label() {
        return kind == AttachmentKind.LINK && fileName != null ? "[" + fileName + "]" : kind.label();
    }

    private static boolean isWebUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
