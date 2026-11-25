package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.connecta.backend.config.AttachmentProperties;
import org.junit.jupiter.api.Test;

class AttachmentValidatorTests {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7'};

    private final AttachmentValidator validator = new AttachmentValidator(new AttachmentProperties(PDF.length));

    @Test
    void returnsTheTypeOfAValidFileAtTheLimit() {
        assertEquals(AttachmentType.PDF, validator.validate(PDF, "application/pdf"));
    }

    @Test
    void anEmptyFileIsRejected() {
        assertThrows(EmptyAttachmentException.class, () -> validator.validate(new byte[0], "application/pdf"));
    }

    @Test
    void aFileOneByteOverTheLimitIsRejectedWithTheLimitInTheMessage() {
        AttachmentTooLargeException e = assertThrows(AttachmentTooLargeException.class,
                () -> validator.validate(new byte[PDF.length + 1], "application/pdf"));

        assertEquals("The file is larger than the 8 byte limit.", e.getMessage());
    }

    @Test
    void theDefaultLimitIsEightMegabytes() {
        assertEquals(8L * 1024 * 1024, new AttachmentProperties(0).maxBytes());
        assertEquals("The file is larger than the 8 MB limit.",
                new AttachmentTooLargeException(8L * 1024 * 1024).getMessage());
    }

    @Test
    void aDisallowedTypeIsRejected() {
        assertThrows(UnsupportedAttachmentTypeException.class, () -> validator.validate(PDF, "text/plain"));
    }
}
