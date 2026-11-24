package com.connecta.backend.meta.service;

import java.net.URI;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The one place that decides how a Meta access token travels on a Graph call: as an {@code Authorization: Bearer}
 * header, never in the URL, so it cannot end up in a logged or recorded address. Used by every Messenger and
 * Instagram send, profile and history client.
 */
final class GraphAuth {

    private static final String TOKEN_PARAM = "access_token";

    private GraphAuth() {
    }

    /** Header customizer for {@code RequestHeadersSpec.headers(...)}. */
    static Consumer<HttpHeaders> bearer(String token) {
        return headers -> headers.setBearerAuth(token);
    }

    /** A paging link without the token Meta embeds in it, so following it never sends the token twice. */
    static URI withoutToken(URI link) {
        return UriComponentsBuilder.fromUri(link).replaceQueryParam(TOKEN_PARAM).build(true).toUri();
    }
}
