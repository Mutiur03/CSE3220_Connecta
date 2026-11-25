package com.connecta.backend.inbox.service;

import com.connecta.backend.file.service.StoredFileService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Keeps our own copy of media that arrived from Meta, because Meta's links expire. Not transactional: each
 * attachment is claimed, downloaded outside any transaction through {@link MediaDownloader} (Adapter), checked
 * against the attachment allow-list (type by magic bytes, of the attachment's kind) and saved by
 * {@link AttachmentMirrorStore}. A failure keeps the Meta URL, is logged by reason only (never the URL, which
 * can carry a token) and is retried by the sweep. Without PUBLIC_BASE_URL nothing is copied, since the copy
 * could not be linked. Attachments our agents sent have no source URL and are never candidates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentMirrorService {

    private final AttachmentMirrorStore store;
    private final MediaDownloader downloader;
    private final StoredFileService files;

    /** Copies the message's attachments (right after it was received). Returns how many were copied. */
    public int mirrorMessage(UUID organizationId, UUID messageId) {
        return mirrorAll(() -> store.pendingOfMessage(organizationId, messageId));
    }

    /** Copies one batch of the organization's pending attachments (after a history import). */
    public int mirrorPending(UUID organizationId) {
        return mirrorAll(() -> store.pending(organizationId));
    }

    /** Copies one batch of pending attachments of every organization (the retry sweep). */
    public int mirrorPending() {
        return mirrorAll(store::pending);
    }

    private int mirrorAll(Supplier<List<MirrorCandidate>> candidates) {
        if (!files.hasPublicBaseUrl()) {
            log.debug("PUBLIC_BASE_URL is not set; incoming media is not copied");
            return 0;
        }
        int copied = 0;
        for (MirrorCandidate candidate : candidates.get()) {
            if (mirror(candidate)) {
                copied++;
            }
        }
        return copied;
    }

    private boolean mirror(MirrorCandidate candidate) {
        if (!store.claim(candidate)) {
            return false;
        }
        try {
            DownloadedMedia media = downloader.download(candidate.sourceUrl());
            Optional<AttachmentType> type = AttachmentType.resolve(media.contentType(), candidate.kind(),
                    media.bytes());
            if (type.isEmpty()) {
                log.info("Attachment {} not copied: unsupported type", candidate.attachmentId());
                return false;
            }
            return store.saveCopy(candidate, media.bytes(), type.get().contentType());
        } catch (MediaDownloadException e) {
            log.info("Attachment {} not copied: {}", candidate.attachmentId(), e.reason());
        } catch (RuntimeException e) {
            log.warn("Attachment {} not copied: {}", candidate.attachmentId(), e.getClass().getSimpleName());
        }
        return false;
    }
}
