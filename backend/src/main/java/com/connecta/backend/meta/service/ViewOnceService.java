package com.connecta.backend.meta.service;

import com.connecta.backend.config.ViewOnceProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.service.DisappearingPhotoService;
import com.connecta.backend.meta.repository.MetaViewOnceMarkRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hide policy for view-once photos. Instagram sends one as two events with the same millisecond timestamp:
 * the sender's echo (type "ephemeral", no media) and the receiver's event (a normal image). The echo is recorded
 * as a mark by its timestamp, even when the sender is not a connected account; the receiver's image is then
 * recognised by that timestamp. This is a heuristic: two unrelated messages in the same millisecond could be
 * confused, which is acceptable because marks live only {@code app.meta.view-once.mark-ttl} and the failure
 * direction is to hide a photo, not to expose one. With {@code hide} off nothing is changed except the mark.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViewOnceService {

    private final MetaViewOnceMarkRepository marks;
    private final DisappearingPhotoService stored;
    private final ViewOnceProperties properties;
    private final Clock clock;

    /** Remembers the timestamp of a sender's view-once echo; other messages are ignored. */
    @Transactional
    public void recordIfEcho(NormalizedMessage message) {
        if (message.isDisappearingEcho()) {
            marks.record(message.sentAt().toEpochMilli(), clock.instant());
        }
    }

    /**
     * The message to store: with hide on, a received image whose timestamp is marked becomes the label without
     * any attachment (so no file is ever downloaded); otherwise the message unchanged.
     */
    @Transactional(readOnly = true)
    public NormalizedMessage forStorage(NormalizedMessage message) {
        if (properties.hide() && isReceivedPhoto(message) && marks.existsById(message.sentAt().toEpochMilli())) {
            return message.asDisappearing();
        }
        return message;
    }

    /** Echo after the photo: with hide on, converts the organization's already stored received photo. */
    @Transactional
    public void hideStoredPhoto(UUID organizationId, NormalizedMessage message) {
        if (properties.hide() && message.isDisappearingEcho()) {
            int converted = stored.convert(organizationId, message.sentAt());
            if (converted > 0) {
                log.info("Converted {} stored view-once photo(s) to a note", converted);
            }
        }
    }

    /** Deletes expired marks; returns how many. */
    @Transactional
    public int deleteExpiredMarks() {
        return marks.deleteCreatedBefore(clock.instant().minus(properties.markTtl()));
    }

    private static boolean isReceivedPhoto(NormalizedMessage message) {
        return message.direction() == NormalizedMessage.Direction.INBOUND && !message.disappearing()
                && message.attachments().stream().anyMatch(a -> a.kind() == AttachmentKind.IMAGE);
    }
}
