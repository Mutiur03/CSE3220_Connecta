package com.connecta.backend.meta.controller;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Sends the browser back to the web app after a Meta callback: to the organization's Integrations page with the
 * result, or with an error code. Only the path is built here; the page itself checks the admin's access.
 */
final class MetaCallbackRedirects {

    private MetaCallbackRedirects() {
    }

    static ResponseEntity<Void> to(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    static String integrations(String frontendBase, UUID organizationId, String query) {
        return frontendBase + "/organizations/" + organizationId + "/integrations?" + query;
    }

    /** Used when the organization is not known, for example the state is missing or was altered. */
    static String organizations(String frontendBase, String errorCode) {
        return frontendBase + "/organizations?metaError=" + errorCode;
    }
}
