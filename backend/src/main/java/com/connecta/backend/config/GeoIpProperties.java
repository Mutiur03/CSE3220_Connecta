package com.connecta.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Path of a MaxMind GeoLite2 City .mmdb file (app.geoip.database-path, from GEOIP_DB_PATH). Blank turns it off. */
@ConfigurationProperties(prefix = "app.geoip")
public record GeoIpProperties(String databasePath) {

    public boolean configured() {
        return databasePath != null && !databasePath.isBlank();
    }
}
