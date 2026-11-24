package com.connecta.backend.meta.dto;

import com.connecta.backend.meta.domain.HistoryImportFailure;
import com.connecta.backend.meta.domain.MetaConnectedPage;
import java.time.Instant;

/**
 * A connected Facebook Page. The Page token is never returned. {@code historyImportError} is set while the last
 * import of past conversations failed ({@code PERMISSION} or {@code FETCH}), else null.
 */
public record MetaPageResponse(String pageId, String name, Instant connectedAt,
        HistoryImportFailure historyImportError, Instant historyImportFailedAt) {

    public static MetaPageResponse from(MetaConnectedPage page) {
        return new MetaPageResponse(page.getPageId(), page.getPageName(), page.getUpdatedAt(),
                page.getHistoryImportError(), page.getHistoryImportFailedAt());
    }
}
