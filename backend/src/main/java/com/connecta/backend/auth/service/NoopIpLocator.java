package com.connecta.backend.auth.service;

import java.util.Optional;

/** Used when no GeoIP database is configured: sessions simply have no location. */
public class NoopIpLocator implements IpLocator {

    @Override
    public Optional<String> locate(String ip) {
        return Optional.empty();
    }
}
