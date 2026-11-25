package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachmentMirrorSweepJobTests {

    private final List<Runnable> queued = new ArrayList<>();
    private AttachmentMirrorService mirror;
    private AttachmentMirrorSweepJob job;

    @BeforeEach
    void setUp() {
        mirror = mock(AttachmentMirrorService.class);
        job = new AttachmentMirrorSweepJob(mirror, queued::add);
    }

    @Test
    void theSweepHandsTheBatchToTheMirrorExecutorInsteadOfRunningOnTheScheduler() {
        job.sweep();

        verifyNoInteractions(mirror);
        queued.forEach(Runnable::run);
        verify(mirror).mirrorPending();
    }

    @Test
    void aFailedBatchIsLoggedAndLeftForTheNextSweep() {
        when(mirror.mirrorPending()).thenThrow(new IllegalStateException("db down"));

        job.sweep();

        assertDoesNotThrow(() -> queued.forEach(Runnable::run));
    }
}
