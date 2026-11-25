package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.inbox.domain.AttachmentKind;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

class AttachmentTypeTests {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10);
    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
    private static final byte[] GIF89 = bytes('G', 'I', 'F', '8', '9', 'a', 1, 0);
    private static final byte[] GIF87 = bytes('G', 'I', 'F', '8', '7', 'a', 1, 0);
    private static final byte[] MP4 = bytes(0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm');
    private static final byte[] ID3 = bytes('I', 'D', '3', 4, 0, 0);
    private static final byte[] MP3_FRAME = bytes(0xFF, 0xFB, 0x90, 0x64);
    private static final byte[] ADTS = bytes(0xFF, 0xF1, 0x50, 0x80);
    private static final byte[] WAV = bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'A', 'V', 'E', 'f', 'm', 't');
    private static final byte[] PDF = bytes('%', 'P', 'D', 'F', '-', '1', '.', '7');
    private static final byte[] HTML = bytes('<', 'h', 't', 'm', 'l', '>');

    static Stream<Arguments> allowed() {
        return Stream.of(
                Arguments.of("image/jpeg", JPEG, AttachmentType.JPEG, AttachmentKind.IMAGE),
                Arguments.of("image/png", PNG, AttachmentType.PNG, AttachmentKind.IMAGE),
                Arguments.of("image/gif", GIF89, AttachmentType.GIF, AttachmentKind.IMAGE),
                Arguments.of("image/gif", GIF87, AttachmentType.GIF, AttachmentKind.IMAGE),
                Arguments.of("video/mp4", MP4, AttachmentType.MP4_VIDEO, AttachmentKind.VIDEO),
                Arguments.of("audio/mpeg", ID3, AttachmentType.MPEG_AUDIO, AttachmentKind.AUDIO),
                Arguments.of("audio/mpeg", MP3_FRAME, AttachmentType.MPEG_AUDIO, AttachmentKind.AUDIO),
                Arguments.of("audio/mp4", MP4, AttachmentType.MP4_AUDIO, AttachmentKind.AUDIO),
                Arguments.of("audio/x-m4a", MP4, AttachmentType.MP4_AUDIO, AttachmentKind.AUDIO),
                Arguments.of("audio/aac", ADTS, AttachmentType.AAC, AttachmentKind.AUDIO),
                Arguments.of("audio/wav", WAV, AttachmentType.WAV, AttachmentKind.AUDIO),
                Arguments.of("audio/x-wav", WAV, AttachmentType.WAV, AttachmentKind.AUDIO),
                Arguments.of("application/pdf", PDF, AttachmentType.PDF, AttachmentKind.FILE));
    }

    @ParameterizedTest(name = "{0} with matching bytes is {2}")
    @MethodSource("allowed")
    void acceptsAnAllowedTypeWhoseBytesMatch(String declared, byte[] content, AttachmentType expected,
            AttachmentKind kind) {
        Optional<AttachmentType> detected = AttachmentType.detect(declared, content);

        assertEquals(Optional.of(expected), detected);
        assertEquals(kind, detected.get().kind());
    }

    @Nested
    class Rejected {

        @Test
        void aTypeOutsideTheAllowList() {
            assertTrue(AttachmentType.detect("text/html", HTML).isEmpty());
            assertTrue(AttachmentType.detect("image/svg+xml", PNG).isEmpty());
        }

        @Test
        void bytesThatAreNotTheDeclaredType() {
            assertTrue(AttachmentType.detect("image/png", JPEG).isEmpty());
            assertTrue(AttachmentType.detect("application/pdf", HTML).isEmpty());
            assertTrue(AttachmentType.detect("audio/mpeg", ADTS).isEmpty());
            assertTrue(AttachmentType.detect("audio/aac", MP3_FRAME).isEmpty());
        }

        @Test
        void noDeclaredType() {
            assertTrue(AttachmentType.detect(null, PNG).isEmpty());
        }

        @Test
        void contentShorterThanTheSignature() {
            assertTrue(AttachmentType.detect("image/png", bytes(0x89, 'P')).isEmpty());
            assertTrue(AttachmentType.detect("video/mp4", bytes(0, 0, 0)).isEmpty());
        }
    }

    @Nested
    class DeclaredType {

        @Test
        void ignoresCaseAndParameters() {
            assertEquals(Optional.of(AttachmentType.PNG), AttachmentType.detect(" Image/PNG; charset=binary", PNG));
        }

        @Test
        void anAliasIsStoredWithTheCanonicalType() {
            assertEquals("audio/wav", AttachmentType.detect("audio/x-wav", WAV).orElseThrow().contentType());
            assertEquals("audio/mpeg", AttachmentType.detect("audio/mp3", ID3).orElseThrow().contentType());
        }
    }

    @Nested
    class Resolve {

        @Test
        void theDeclaredTypeWinsWhenItIsOfTheKind() {
            assertEquals(Optional.of(AttachmentType.PNG),
                    AttachmentType.resolve("image/png", AttachmentKind.IMAGE, PNG));
        }

        @Test
        void anOctetStreamFallsBackToTheTypeOfTheKindTheBytesMatch() {
            assertEquals(Optional.of(AttachmentType.JPEG),
                    AttachmentType.resolve("application/octet-stream", AttachmentKind.IMAGE, JPEG));
            assertEquals(Optional.of(AttachmentType.JPEG), AttachmentType.resolve(null, AttachmentKind.IMAGE, JPEG));
        }

        @Test
        void bytesOfAnotherKindAreRefused() {
            assertTrue(AttachmentType.resolve("image/png", AttachmentKind.AUDIO, PNG).isEmpty());
            assertTrue(AttachmentType.resolve("text/html", AttachmentKind.IMAGE, bytes('<', 'h', 't')).isEmpty());
        }
    }
}
