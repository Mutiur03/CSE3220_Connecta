package com.connecta.backend.inbox.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.config.AttachmentMirrorProperties;
import com.connecta.backend.inbox.service.HttpMediaDownloader.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The SSRF guards and limits of the downloader, with a fake name lookup and a fake HTTP answer per URL. */
class HttpMediaDownloaderTests {

    private static final String MEDIA = "https://scontent.xx.fbcdn.net/v/photo.jpg?oh=secret";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
    private static final long MAX_BYTES = 16;

    private final Map<String, InetAddress[]> dns = new HashMap<>();
    private final Map<String, Response> answers = new HashMap<>();
    private final List<URI> requested = new ArrayList<>();
    private HttpMediaDownloader downloader;

    @BeforeEach
    void setUp() throws UnknownHostException {
        AttachmentMirrorProperties settings = new AttachmentMirrorProperties(MAX_BYTES,
                List.of("fbcdn.net", "fbsbx.com"), null, null, null, null, null, null);
        downloader = new HttpMediaDownloader(settings, host -> {
            InetAddress[] found = dns.get(host);
            if (found == null) {
                throw new UnknownHostException(host);
            }
            return found;
        }, target -> {
            requested.add(target);
            Response answer = answers.get(target.toString());
            if (answer == null) {
                throw new IOException("connection refused");
            }
            return answer;
        });
        dns.put("scontent.xx.fbcdn.net", addresses("157.240.1.1"));
        dns.put("lookaside.fbsbx.com", addresses("157.240.2.2"));
    }

    private static InetAddress[] addresses(String... literals) throws UnknownHostException {
        InetAddress[] found = new InetAddress[literals.length];
        for (int i = 0; i < literals.length; i++) {
            found[i] = InetAddress.getByName(literals[i]);
        }
        return found;
    }

    private static Response ok(byte[] body, String contentType) {
        return new Response(200, null, contentType, body.length, new ByteArrayInputStream(body));
    }

    private static Response redirect(String location) {
        return new Response(302, location, null, -1, null);
    }

    private MediaDownloadFailure failure(String url) {
        return assertThrows(MediaDownloadException.class, () -> downloader.download(url)).reason();
    }

    @Test
    void downloadsTheFileFromAnAllowedPublicHost() {
        answers.put(MEDIA, ok(JPEG, "image/jpeg"));

        DownloadedMedia media = downloader.download(MEDIA);

        assertArrayEquals(JPEG, media.bytes());
        assertEquals("image/jpeg", media.contentType());
    }

    @Test
    void theExceptionNamesTheReasonButNeverTheUrl() {
        MediaDownloadException e = assertThrows(MediaDownloadException.class,
                () -> downloader.download("https://evil.example/x?token=secret"));

        assertFalse(e.getMessage().contains("secret"));
        assertFalse(e.getMessage().contains("evil"));
    }

    @Nested
    class Scheme {

        @ParameterizedTest
        @ValueSource(strings = {"http://scontent.xx.fbcdn.net/a.jpg", "ftp://scontent.xx.fbcdn.net/a.jpg",
                "file:///etc/passwd", "scontent.xx.fbcdn.net/a.jpg"})
        void onlyHttpsIsFetched(String url) {
            assertEquals(MediaDownloadFailure.NOT_HTTPS, failure(url));
            assertTrue(requested.isEmpty());
        }

        @Test
        void aMalformedUrlIsRefused() {
            assertEquals(MediaDownloadFailure.INVALID_URL, failure("https://bad host/x"));
            assertEquals(MediaDownloadFailure.INVALID_URL, failure(null));
        }
    }

    @Nested
    class HostAllowList {

        @ParameterizedTest
        @ValueSource(strings = {"https://example.com/a.jpg", "https://evilfbcdn.net/a.jpg",
                "https://fbcdn.net.evil.com/a.jpg", "https://157.240.1.1/a.jpg",
                "https://scontent.xx.fbcdn.net:8443/a.jpg", "https://user@evil.com/a.jpg"})
        void aHostOffTheListOrAnOtherPortIsRefused(String url) {
            assertEquals(MediaDownloadFailure.HOST_NOT_ALLOWED, failure(url));
            assertTrue(requested.isEmpty());
        }

        @Test
        void theSuffixItselfAndUpperCaseHostsAreAllowed() throws UnknownHostException {
            dns.put("fbcdn.net", addresses("157.240.3.3"));
            answers.put("https://FBCDN.net/a.jpg", ok(JPEG, "image/jpeg"));

            assertArrayEquals(JPEG, downloader.download("https://FBCDN.net/a.jpg").bytes());
        }

        @Test
        void anUnknownHostIsRefused() {
            assertEquals(MediaDownloadFailure.UNRESOLVABLE_HOST, failure("https://gone.fbcdn.net/a.jpg"));
        }
    }

    @Nested
    class PrivateAddresses {

        @ParameterizedTest
        @ValueSource(strings = {"127.0.0.1", "10.0.0.5", "172.16.3.4", "192.168.1.1", "169.254.169.254",
                "0.0.0.0", "100.64.0.1", "224.0.0.1", "255.255.255.255", "::1", "::", "fe80::1", "fd00::1",
                "::ffff:127.0.0.1"})
        void anAllowedNameThatResolvesToANonPublicAddressIsRefused(String address) throws UnknownHostException {
            dns.put("internal.fbcdn.net", addresses("157.240.1.1", address));

            assertEquals(MediaDownloadFailure.PRIVATE_ADDRESS, failure("https://internal.fbcdn.net/a.jpg"));
            assertTrue(requested.isEmpty());
        }

        @Test
        void publicAddressesPass() throws UnknownHostException {
            assertTrue(HttpMediaDownloader.isPublic(InetAddress.getByName("157.240.1.1")));
            assertTrue(HttpMediaDownloader.isPublic(InetAddress.getByName("2a03:2880:f12f:83:face:b00c:0:25de")));
        }
    }

    @Nested
    class Redirects {

        @Test
        void aRedirectToAnotherAllowedHostIsFollowed() {
            answers.put("https://lookaside.fbsbx.com/media?id=1", redirect(MEDIA));
            answers.put(MEDIA, ok(JPEG, "image/jpeg"));

            assertArrayEquals(JPEG, downloader.download("https://lookaside.fbsbx.com/media?id=1").bytes());
            assertEquals(2, requested.size());
        }

        @Test
        void aRedirectToAForeignHostIsRefusedBeforeItIsRequested() {
            answers.put(MEDIA, redirect("https://attacker.example/steal"));

            assertEquals(MediaDownloadFailure.HOST_NOT_ALLOWED, failure(MEDIA));
            assertEquals(1, requested.size());
        }

        @Test
        void aRedirectToPlainHttpIsRefused() {
            answers.put(MEDIA, redirect("http://scontent.xx.fbcdn.net/a.jpg"));

            assertEquals(MediaDownloadFailure.NOT_HTTPS, failure(MEDIA));
        }

        @Test
        void aRedirectToAPrivateAddressIsRefused() throws UnknownHostException {
            dns.put("metadata.fbcdn.net", addresses("169.254.169.254"));
            answers.put(MEDIA, redirect("https://metadata.fbcdn.net/latest"));

            assertEquals(MediaDownloadFailure.PRIVATE_ADDRESS, failure(MEDIA));
            assertEquals(1, requested.size());
        }

        @Test
        void aRelativeRedirectStaysOnTheSameHost() {
            answers.put(MEDIA, redirect("/v/other.jpg"));
            answers.put("https://scontent.xx.fbcdn.net/v/other.jpg", ok(JPEG, "image/jpeg"));

            assertArrayEquals(JPEG, downloader.download(MEDIA).bytes());
        }

        @Test
        void aRedirectLoopStops() {
            answers.put(MEDIA, redirect(MEDIA));

            assertEquals(MediaDownloadFailure.TOO_MANY_REDIRECTS, failure(MEDIA));
            assertEquals(HttpMediaDownloader.MAX_REDIRECTS + 1, requested.size());
        }

        @Test
        void aRedirectWithoutLocationFails() {
            answers.put(MEDIA, redirect(null));

            assertEquals(MediaDownloadFailure.HTTP_ERROR, failure(MEDIA));
        }
    }

    @Nested
    class Limits {

        @Test
        void aDeclaredLengthOverTheCapIsRefusedWithoutReading() {
            TrackingStream body = new TrackingStream(new byte[(int) MAX_BYTES + 1]);
            answers.put(MEDIA, new Response(200, null, "image/jpeg", MAX_BYTES + 1, body));

            assertEquals(MediaDownloadFailure.TOO_LARGE, failure(MEDIA));
            assertEquals(0, body.read);
            assertTrue(body.closed);
        }

        @Test
        void aBodyLongerThanTheCapIsAbortedAfterCapPlusOneBytes() {
            TrackingStream body = new TrackingStream(new byte[1000]);
            answers.put(MEDIA, new Response(200, null, "image/jpeg", -1, body));

            assertEquals(MediaDownloadFailure.TOO_LARGE, failure(MEDIA));
            assertEquals(MAX_BYTES + 1, body.read);
        }

        @Test
        void aBodyOfExactlyTheCapIsKept() {
            answers.put(MEDIA, ok(new byte[(int) MAX_BYTES], "image/jpeg"));

            assertEquals(MAX_BYTES, downloader.download(MEDIA).bytes().length);
        }

        @Test
        void anEmptyBodyFails() {
            answers.put(MEDIA, ok(new byte[0], "image/jpeg"));

            assertEquals(MediaDownloadFailure.EMPTY, failure(MEDIA));
        }

        @Test
        void anErrorStatusFails() {
            answers.put(MEDIA, new Response(403, null, "text/html", -1, null));

            assertEquals(MediaDownloadFailure.HTTP_ERROR, failure(MEDIA));
        }

        @Test
        void aNetworkErrorFails() {
            assertEquals(MediaDownloadFailure.NETWORK, failure("https://scontent.xx.fbcdn.net/unanswered.jpg"));
        }
    }

    /** Counts the bytes read, so the cap is proven to stop reading. */
    private static final class TrackingStream extends InputStream {

        private final ByteArrayInputStream source;
        private long read;
        private boolean closed;

        TrackingStream(byte[] bytes) {
            this.source = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            int next = source.read();
            if (next >= 0) {
                read++;
            }
            return next;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            int count = source.read(buffer, offset, length);
            if (count > 0) {
                read += count;
            }
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
