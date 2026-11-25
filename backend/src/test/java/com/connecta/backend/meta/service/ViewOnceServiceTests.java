package com.connecta.backend.meta.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.connecta.backend.config.ViewOnceProperties;
import com.connecta.backend.inbox.domain.AttachmentKind;
import com.connecta.backend.inbox.service.DisappearingPhotoService;
import com.connecta.backend.meta.domain.WebhookObject;
import com.connecta.backend.meta.repository.MetaViewOnceMarkRepository;
import com.connecta.backend.meta.service.NormalizedMessage.Direction;
import com.connecta.backend.support.TestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ViewOnceServiceTests {

    private static final Instant SENT = Instant.ofEpochMilli(1700000000123L);

    private MetaViewOnceMarkRepository marks;
    private DisappearingPhotoService stored;

    @BeforeEach
    void setUp() {
        marks = mock(MetaViewOnceMarkRepository.class);
        stored = mock(DisappearingPhotoService.class);
    }

    private ViewOnceService service(boolean hide) {
        return new ViewOnceService(marks, stored, new ViewOnceProperties(hide, null, null), TestClock.FIXED);
    }

    private static NormalizedMessage photo(Direction direction) {
        return new NormalizedMessage(WebhookObject.INSTAGRAM, "m", "p", direction, "[Photo]", SENT,
                List.of(new NormalizedAttachment(AttachmentKind.IMAGE, "https://cdn.example/a.jpg", null, null)));
    }

    private static NormalizedMessage echo() {
        return photo(Direction.OUTBOUND).asDisappearing();
    }

    @Test
    void onlyAnEphemeralEchoIsRecorded() {
        service(false).recordIfEcho(photo(Direction.INBOUND));
        service(false).recordIfEcho(photo(Direction.OUTBOUND));
        verify(marks, never()).record(anyLong(), any());

        service(false).recordIfEcho(echo());

        verify(marks).record(SENT.toEpochMilli(), TestClock.NOW);
    }

    @Test
    void aMarkedReceivedPhotoBecomesTheNoteOnlyWhenHideIsOn() {
        when(marks.existsById(SENT.toEpochMilli())).thenReturn(true);
        NormalizedMessage received = photo(Direction.INBOUND);

        assertSame(received, service(false).forStorage(received));
        NormalizedMessage hidden = service(true).forStorage(received);

        assertEquals("[Disappearing photo]", hidden.text());
        assertEquals(List.of(), hidden.attachments());
    }

    @Test
    void anUnmarkedOrNonPhotoMessageIsReturnedAsIs() {
        NormalizedMessage received = photo(Direction.INBOUND);
        assertSame(received, service(true).forStorage(received));
        when(marks.existsById(SENT.toEpochMilli())).thenReturn(true);
        NormalizedMessage text = new NormalizedMessage(WebhookObject.INSTAGRAM, "t", "p", Direction.INBOUND, "hi",
                SENT, List.of());
        assertSame(text, service(true).forStorage(text));
    }

    @Test
    void storedPhotosAreConvertedForAnEchoOnlyWhenHideIsOn() {
        UUID org = UUID.randomUUID();

        service(false).hideStoredPhoto(org, echo());
        service(true).hideStoredPhoto(org, photo(Direction.INBOUND));
        verify(stored, never()).convert(any(), any());

        service(true).hideStoredPhoto(org, echo());

        verify(stored).convert(org, SENT);
    }

    @Test
    void expiredMarksAreDeletedByTheCutoff() {
        when(marks.deleteCreatedBefore(TestClock.NOW.minus(Duration.ofHours(1)))).thenReturn(3);

        assertEquals(3, service(false).deleteExpiredMarks());
    }

    @Test
    void theSweepJobDeletesExpiredMarksAndSurvivesAFailure() {
        ViewOnceService viewOnce = mock(ViewOnceService.class);
        ViewOnceMarkSweepJob job = new ViewOnceMarkSweepJob(viewOnce);

        job.sweep();
        verify(viewOnce).deleteExpiredMarks();

        when(viewOnce.deleteExpiredMarks()).thenThrow(new IllegalStateException("db down"));
        job.sweep();
    }
}
