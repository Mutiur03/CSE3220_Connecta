package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.connecta.backend.file.service.StoredFileService;
import com.connecta.backend.inbox.domain.AttachmentKind;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AttachmentMirrorServiceTests {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7};
    private static final byte[] M4A = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
    private static final UUID ORG = UUID.randomUUID();
    private static final UUID MESSAGE = UUID.randomUUID();

    private AttachmentMirrorStore store;
    private MediaDownloader downloader;
    private StoredFileService files;
    private AttachmentMirrorService service;
    private MirrorCandidate image;

    @BeforeEach
    void setUp() {
        store = mock(AttachmentMirrorStore.class);
        downloader = mock(MediaDownloader.class);
        files = mock(StoredFileService.class);
        service = new AttachmentMirrorService(store, downloader, files);
        image = new MirrorCandidate(UUID.randomUUID(), ORG, AttachmentKind.IMAGE,
                "https://scontent.fbcdn.net/a.png", "a.png");
        when(files.hasPublicBaseUrl()).thenReturn(true);
        when(store.pendingOfMessage(ORG, MESSAGE)).thenReturn(List.of(image));
        when(store.claim(any())).thenReturn(true);
        when(store.saveCopy(any(), any(), anyString())).thenReturn(true);
    }

    @Nested
    class Copies {

        @Test
        void downloadsTheSourceAndSavesItWithTheCheckedType() {
            when(downloader.download(image.sourceUrl())).thenReturn(new DownloadedMedia(PNG, "image/png"));

            assertEquals(1, service.mirrorMessage(ORG, MESSAGE));

            verify(store).saveCopy(image, PNG, "image/png");
        }

        @Test
        void anOctetStreamWhoseBytesAreOfTheKindIsStoredAsThatType() {
            when(downloader.download(image.sourceUrl()))
                    .thenReturn(new DownloadedMedia(PNG, "application/octet-stream"));

            service.mirrorMessage(ORG, MESSAGE);

            verify(store).saveCopy(image, PNG, "image/png");
        }

        @Test
        void aVoiceNoteDeclaredAsVideoIsStoredAsAudio() {
            MirrorCandidate voice = new MirrorCandidate(UUID.randomUUID(), ORG, AttachmentKind.AUDIO,
                    "https://cdn.fbsbx.com/v.mp4", null);
            when(store.pendingOfMessage(ORG, MESSAGE)).thenReturn(List.of(voice));
            when(downloader.download(voice.sourceUrl())).thenReturn(new DownloadedMedia(M4A, "video/mp4"));

            service.mirrorMessage(ORG, MESSAGE);

            verify(store).saveCopy(voice, M4A, "audio/mp4");
        }

        @Test
        void theHistoryImportAndTheSweepCopyTheirBatches() {
            when(store.pending(ORG)).thenReturn(List.of(image));
            when(store.pending()).thenReturn(List.of(image));
            when(downloader.download(image.sourceUrl())).thenReturn(new DownloadedMedia(PNG, "image/png"));

            assertEquals(1, service.mirrorPending(ORG));
            assertEquals(1, service.mirrorPending());
        }
    }

    @Nested
    class Failures {

        @Test
        void aFailedDownloadSavesNothingSoTheMetaUrlStays() {
            when(downloader.download(image.sourceUrl()))
                    .thenThrow(new MediaDownloadException(MediaDownloadFailure.HTTP_ERROR));

            assertEquals(0, service.mirrorMessage(ORG, MESSAGE));

            verify(store, never()).saveCopy(any(), any(), anyString());
        }

        @Test
        void bytesOfAnotherTypeThanDeclaredAndKindAreNotStored() {
            byte[] html = "<html><script>".getBytes();
            when(downloader.download(image.sourceUrl())).thenReturn(new DownloadedMedia(html, "image/png"));

            assertEquals(0, service.mirrorMessage(ORG, MESSAGE));

            verify(store, never()).saveCopy(any(), any(), anyString());
        }

        @Test
        void anUnexpectedErrorOnOneAttachmentDoesNotStopTheNext() {
            MirrorCandidate second = new MirrorCandidate(UUID.randomUUID(), ORG, AttachmentKind.IMAGE,
                    "https://scontent.fbcdn.net/b.png", null);
            when(store.pendingOfMessage(ORG, MESSAGE)).thenReturn(List.of(image, second));
            when(downloader.download(image.sourceUrl())).thenThrow(new IllegalStateException("boom"));
            when(downloader.download(second.sourceUrl())).thenReturn(new DownloadedMedia(PNG, "image/png"));

            assertEquals(1, service.mirrorMessage(ORG, MESSAGE));

            verify(store).saveCopy(second, PNG, "image/png");
        }

        @Test
        void withoutAPublicBaseUrlNothingIsLookedUpOrDownloaded() {
            when(files.hasPublicBaseUrl()).thenReturn(false);

            assertEquals(0, service.mirrorMessage(ORG, MESSAGE));

            verifyNoInteractions(store, downloader);
        }
    }

    @Nested
    class Idempotence {

        @Test
        void anAttachmentClaimedByAnotherWorkerIsNotDownloaded() {
            when(store.claim(image)).thenReturn(false);

            assertEquals(0, service.mirrorMessage(ORG, MESSAGE));

            verifyNoInteractions(downloader);
            verify(store, never()).saveCopy(any(), any(), anyString());
        }

        @Test
        void aCopySavedMeanwhileIsNotCounted() {
            when(downloader.download(image.sourceUrl())).thenReturn(new DownloadedMedia(PNG, "image/png"));
            when(store.saveCopy(eq(image), any(), anyString())).thenReturn(false);

            assertEquals(0, service.mirrorMessage(ORG, MESSAGE));
        }
    }
}
