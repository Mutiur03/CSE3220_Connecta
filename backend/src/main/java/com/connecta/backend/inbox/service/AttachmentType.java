package com.connecta.backend.inbox.service;

import com.connecta.backend.inbox.domain.AttachmentKind;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The file types an agent may send. A file is accepted only when its declared content type is on this list AND
 * its leading bytes match that type, so a renamed executable or HTML page is never stored or served. The first
 * content type of each entry is the canonical one that is stored; the others are aliases browsers send.
 */
public enum AttachmentType {
    JPEG(AttachmentKind.IMAGE, bytes -> startsWith(bytes, 0, 0xFF, 0xD8, 0xFF), "image/jpeg", "image/jpg"),
    PNG(AttachmentKind.IMAGE, bytes -> startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A),
            "image/png"),
    GIF(AttachmentKind.IMAGE, bytes -> startsWith(bytes, 0, 'G', 'I', 'F', '8', '7', 'a')
            || startsWith(bytes, 0, 'G', 'I', 'F', '8', '9', 'a'), "image/gif"),
    MP4_VIDEO(AttachmentKind.VIDEO, AttachmentType::isIsoMedia, "video/mp4"),
    MPEG_AUDIO(AttachmentKind.AUDIO, bytes -> startsWith(bytes, 0, 'I', 'D', '3') || isMpegFrame(bytes),
            "audio/mpeg", "audio/mp3"),
    MP4_AUDIO(AttachmentKind.AUDIO, AttachmentType::isIsoMedia, "audio/mp4", "audio/x-m4a", "audio/m4a"),
    AAC(AttachmentKind.AUDIO, AttachmentType::isAdtsFrame, "audio/aac", "audio/x-aac"),
    WAV(AttachmentKind.AUDIO, bytes -> startsWith(bytes, 0, 'R', 'I', 'F', 'F')
            && startsWith(bytes, 8, 'W', 'A', 'V', 'E'), "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"),
    PDF(AttachmentKind.FILE, bytes -> startsWith(bytes, 0, '%', 'P', 'D', 'F', '-'), "application/pdf");

    private final AttachmentKind kind;
    private final Predicate<byte[]> signature;
    private final List<String> contentTypes;

    AttachmentType(AttachmentKind kind, Predicate<byte[]> signature, String... contentTypes) {
        this.kind = kind;
        this.signature = signature;
        this.contentTypes = List.of(contentTypes);
    }

    public AttachmentKind kind() {
        return kind;
    }

    /** The content type the file is stored and served with. */
    public String contentType() {
        return contentTypes.get(0);
    }

    /** The type the declared content type names, when the bytes really are that type. Empty otherwise. */
    public static Optional<AttachmentType> detect(String declaredContentType, byte[] bytes) {
        String declared = normalize(declaredContentType);
        return Arrays.stream(values())
                .filter(type -> type.contentTypes.contains(declared) && type.signature.test(bytes))
                .findFirst();
    }

    /**
     * The type of a downloaded file that must be of {@code kind}: the declared type when the bytes match it and it
     * is of that kind, else the first allowed type of that kind whose signature the bytes match (a CDN often sends
     * application/octet-stream, or video/mp4 for a voice note). Empty when no allowed type fits.
     */
    public static Optional<AttachmentType> resolve(String declaredContentType, AttachmentKind kind, byte[] bytes) {
        return detect(declaredContentType, bytes).filter(type -> type.kind == kind)
                .or(() -> Arrays.stream(values())
                        .filter(type -> type.kind == kind && type.signature.test(bytes))
                        .findFirst());
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        String type = parameters < 0 ? contentType : contentType.substring(0, parameters);
        return type.strip().toLowerCase(Locale.ROOT);
    }

    /** An ISO base media file (MP4, M4A): the first box is "ftyp". */
    private static boolean isIsoMedia(byte[] bytes) {
        return startsWith(bytes, 4, 'f', 't', 'y', 'p');
    }

    /** An MPEG audio frame header: 11 sync bits, then a layer that is not the reserved 00. */
    private static boolean isMpegFrame(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0
                && (bytes[1] & 0x06) != 0;
    }

    /** An AAC ADTS frame header: 12 sync bits, then layer 00. */
    private static boolean isAdtsFrame(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xF6) == 0xF0;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
