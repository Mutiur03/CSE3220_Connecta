package com.connecta.backend.auth.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Offline lookup in a MaxMind GeoLite2 City database: the IP never leaves this server. */
@Slf4j
@RequiredArgsConstructor
public class MaxMindIpLocator implements IpLocator {

    private final DatabaseReader reader;

    @Override
    public Optional<String> locate(String ip) {
        if (ip == null || ip.isBlank()) {
            return Optional.empty();
        }
        try {
            return reader.tryCity(InetAddress.getByName(ip)).flatMap(MaxMindIpLocator::place);
        } catch (IOException | GeoIp2Exception e) {
            log.debug("GeoIP lookup failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<String> place(CityResponse response) {
        // The city is often missing for mobile and ISP ranges: the region (a division or state) is the next best.
        String locality = response.city().name() != null ? response.city().name()
                : response.mostSpecificSubdivision().name();
        return Stream.of(locality, response.country().name())
                .filter(name -> name != null && !name.isBlank())
                .reduce((city, country) -> city + ", " + country);
    }
}
