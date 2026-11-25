package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.domain.Message;
import com.connecta.backend.inbox.domain.MessageAttachment;
import com.connecta.backend.inbox.repository.MessageAttachmentRepository;
import com.connecta.backend.meta.service.NormalizedAttachment;
import com.connecta.backend.meta.service.NormalizedMessage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Brings a stored message in line with what Meta's history returns for it. Only adds and corrects: the body is
 * corrected through {@link Message#correctBody}, attachments Meta returns that the message lacks are added (as
 * Meta media, so the attachment mirror copies them), and nothing is ever deleted. A {@code [Disappearing photo]}
 * message is left alone: Meta returns no media for a view-once photo, and it must never be downgraded or refilled. Sender, conversation, direction,
 * time and read state are never touched. Runs inside the caller's transaction; every read is scoped by the
 * message's organization.
 *
 * <p>An attachment is known when its Meta URL equals a stored attachment's {@code url} or {@code sourceUrl}, so
 * a copied attachment (url rewritten, Meta URL kept in sourceUrl) is never added twice. At most as many are added
 * as Meta returns more than the message holds, so an attachment our agent sent (stored under our own URL) or a
 * Meta link that changed between reads never creates a duplicate.
 */
@Service
@RequiredArgsConstructor
public class MessageReconciler {

    private final MessageAttachmentRepository attachments;

    /** Returns true when the message changed. */
    public boolean reconcile(Message stored, NormalizedMessage fromMeta) {
        if (NormalizedMessage.DISAPPEARING_PHOTO.equals(stored.getBody())) {
            return false;
        }
        boolean bodyCorrected = !isBareFallbackLabel(fromMeta) && stored.correctBody(fromMeta.text());
        boolean attachmentsAdded = addMissingAttachments(stored, fromMeta.attachments());
        return bodyCorrected || attachmentsAdded;
    }

    /**
     * Meta gave neither text nor media (for example the attachments field was refused), so the text is only the
     * generic label and must not overwrite a more specific body.
     */
    private static boolean isBareFallbackLabel(NormalizedMessage fromMeta) {
        return fromMeta.attachments().isEmpty() && AttachmentKind.FILE.label().equals(fromMeta.text());
    }

    private boolean addMissingAttachments(Message stored, List<NormalizedAttachment> fromMeta) {
        if (fromMeta.isEmpty()) {
            return false;
        }
        List<MessageAttachment> held = attachments
                .findByMessage(stored.getOrganization().getId(), List.of(stored.getId()))
                .getOrDefault(stored.getId(), List.of());
        Set<String> knownUrls = knownUrls(held);
        List<MessageAttachment> missing = fromMeta.stream()
                .filter(file -> !knownUrls.contains(file.url()))
                .limit(Math.max(0, fromMeta.size() - held.size()))
                .map(file -> MessageAttachment.fromProvider(stored, file.kind(), file.url(), file.fileName(),
                        file.contentType()))
                .toList();
        if (missing.isEmpty()) {
            return false;
        }
        attachments.saveAll(missing);
        return true;
    }

    private static Set<String> knownUrls(List<MessageAttachment> held) {
        Set<String> urls = new HashSet<>();
        for (MessageAttachment attachment : held) {
            urls.add(attachment.getUrl());
            if (attachment.getSourceUrl() != null) {
                urls.add(attachment.getSourceUrl());
            }
        }
        return urls;
    }
}
