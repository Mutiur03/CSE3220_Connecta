package com.connecta.backend.meta.dto;

import com.connecta.backend.meta.domain.MetaConnectedPage;
import java.time.Instant;

/** A connected Facebook Page. The Page token is never returned. */
public record MetaPageResponse(String pageId, String name, Instant connectedAt) {

    public static MetaPageResponse from(MetaConnectedPage page) {
        return new MetaPageResponse(page.getPageId(), page.getPageName(), page.getUpdatedAt());
    }
}
