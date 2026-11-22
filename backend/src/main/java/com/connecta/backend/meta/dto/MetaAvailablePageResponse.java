package com.connecta.backend.meta.dto;

import com.connecta.backend.meta.service.MetaAvailablePage;

/** A Page the grant includes, for the connect screen. The Page token is never returned. */
public record MetaAvailablePageResponse(String pageId, String name, boolean connected) {

    public static MetaAvailablePageResponse from(MetaAvailablePage page) {
        return new MetaAvailablePageResponse(page.pageId(), page.name(), page.connected());
    }
}
