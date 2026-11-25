package com.connecta.backend.inbox.service;

import com.connecta.backend.config.AttachmentMirrorProperties;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Adapter: downloads media from Meta's CDN over HTTPS with the SSRF guards applied to the URL and to every
 * redirect hop: https on the default port only, a host on the allow-list
 * (app.attachments.mirror-allowed-host-suffixes), and every address the host resolves to public (no loopback,
 * private, link-local, any-local, carrier-grade NAT, multicast or unique-local). Redirects are followed by hand,
 * at most {@link #MAX_REDIRECTS}, each re-checked. The body is read up to app.attachments.mirror-max-bytes and the
 * download is aborted past it.
 */
@Component
public class HttpMediaDownloader implements MediaDownloader {

    static final int MAX_REDIRECTS = 3;

    private static final String HTTPS = "https";
    private static final int HTTPS_PORT = 443;
    private static final int OK = 200;
    private static final int FIRST_REDIRECT = 300;
    private static final int FIRST_ERROR = 400;

    private final AttachmentMirrorProperties settings;
    private final HostResolver resolver;
    private final Transport transport;

    @Autowired
    public HttpMediaDownloader(AttachmentMirrorProperties settings) {
        this(settings, InetAddress::getAllByName, new UrlConnectionTransport(settings));
    }

    HttpMediaDownloader(AttachmentMirrorProperties settings, HostResolver resolver, Transport transport) {
        this.settings = settings;
        this.resolver = resolver;
        this.transport = transport;
    }

    @Override
    public DownloadedMedia download(String url) {
        URI target = parse(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkAllowed(target);
            try (Response response = transport.get(target)) {
                if (response.status() >= FIRST_REDIRECT && response.status() < FIRST_ERROR) {
                    target = redirectTarget(target, response.location());
                    continue;
                }
                if (response.status() != OK) {
                    throw new MediaDownloadException(MediaDownloadFailure.HTTP_ERROR);
                }
                return new DownloadedMedia(readCapped(response), response.contentType());
            } catch (IOException e) {
                throw new MediaDownloadException(MediaDownloadFailure.NETWORK);
            }
        }
        throw new MediaDownloadException(MediaDownloadFailure.TOO_MANY_REDIRECTS);
    }

    private static URI parse(String url) {
        if (url == null) {
            throw new MediaDownloadException(MediaDownloadFailure.INVALID_URL);
        }
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new MediaDownloadException(MediaDownloadFailure.INVALID_URL);
        }
    }

    private static URI redirectTarget(URI from, String location) {
        if (location == null || location.isBlank()) {
            throw new MediaDownloadException(MediaDownloadFailure.HTTP_ERROR);
        }
        try {
            return from.resolve(location);
        } catch (IllegalArgumentException e) {
            throw new MediaDownloadException(MediaDownloadFailure.INVALID_URL);
        }
    }

    /** The SSRF guard, run before every request. */
    private void checkAllowed(URI target) {
        if (!HTTPS.equalsIgnoreCase(target.getScheme())) {
            throw new MediaDownloadException(MediaDownloadFailure.NOT_HTTPS);
        }
        String host = target.getHost() == null ? null : target.getHost().toLowerCase(Locale.ROOT);
        boolean defaultPort = target.getPort() == -1 || target.getPort() == HTTPS_PORT;
        if (host == null || !defaultPort || !isAllowedHost(host)) {
            throw new MediaDownloadException(MediaDownloadFailure.HOST_NOT_ALLOWED);
        }
        // ponytail: the connection resolves the name again (DNS rebinding window); acceptable while only Meta's
        // own domains pass the allow-list. Connect to the checked address if the allow-list ever widens.
        for (InetAddress address : resolve(host)) {
            if (!isPublic(address)) {
                throw new MediaDownloadException(MediaDownloadFailure.PRIVATE_ADDRESS);
            }
        }
    }

    private boolean isAllowedHost(String host) {
        return settings.mirrorAllowedHostSuffixes().stream()
                .anyMatch(suffix -> host.equals(suffix) || host.endsWith("." + suffix));
    }

    private InetAddress[] resolve(String host) {
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new MediaDownloadException(MediaDownloadFailure.UNRESOLVABLE_HOST);
        }
        if (addresses == null || addresses.length == 0) {
            throw new MediaDownloadException(MediaDownloadFailure.UNRESOLVABLE_HOST);
        }
        return addresses;
    }

    /** False for every address that is not on the public internet. */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 0xFF;
        if (bytes.length == 4) {
            int second = bytes[1] & 0xFF;
            boolean thisNetwork = first == 0;
            boolean carrierGradeNat = first == 100 && (second & 0xC0) == 64;
            boolean reserved = first >= 240;
            return !(thisNetwork || carrierGradeNat || reserved);
        }
        boolean uniqueLocal = (first & 0xFE) == 0xFC;
        return !uniqueLocal;
    }

    private byte[] readCapped(Response response) throws IOException {
        long max = settings.mirrorMaxBytes();
        if (response.contentLength() > max) {
            throw new MediaDownloadException(MediaDownloadFailure.TOO_LARGE);
        }
        byte[] bytes = response.body().readNBytes((int) max + 1);
        if (bytes.length > max) {
            throw new MediaDownloadException(MediaDownloadFailure.TOO_LARGE);
        }
        if (bytes.length == 0) {
            throw new MediaDownloadException(MediaDownloadFailure.EMPTY);
        }
        return bytes;
    }

    /** Name lookup, replaced in tests. */
    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    /** One GET that never follows redirects, replaced in tests. */
    @FunctionalInterface
    interface Transport {
        Response get(URI target) throws IOException;
    }

    /**
     * One answer. {@code body} is only set for a 200.
     *
     * @param contentLength the declared length, or -1 when unknown
     */
    record Response(int status, String location, String contentType, long contentLength, InputStream body)
            implements Closeable {

        @Override
        public void close() throws IOException {
            if (body != null) {
                body.close();
            }
        }
    }

    /** The JDK client: connect and read timeouts, no automatic redirects. */
    private static final class UrlConnectionTransport implements Transport {

        private final int timeoutMillis;

        UrlConnectionTransport(AttachmentMirrorProperties settings) {
            this.timeoutMillis = Math.toIntExact(settings.mirrorTimeout().toMillis());
        }

        @Override
        public Response get(URI target) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) target.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            String contentType = connection.getContentType();
            long contentLength = connection.getContentLengthLong();
            InputStream body = status == OK ? connection.getInputStream() : null;
            if (body == null) {
                connection.disconnect();
            }
            return new Response(status, location, contentType, contentLength, body);
        }
    }
}
