package com.connecta.backend.config;

import com.connecta.backend.auth.service.IpLocator;
import com.connecta.backend.auth.service.MaxMindIpLocator;
import com.connecta.backend.auth.service.NoopIpLocator;
import com.maxmind.geoip2.DatabaseReader;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Chooses the {@link IpLocator} once at startup (factory method): the database when present, else none. */
@Slf4j
@Configuration
class GeoIpConfig {

    @Bean
    IpLocator ipLocator(GeoIpProperties props) throws IOException {
        if (!props.configured()) {
            return new NoopIpLocator();
        }
        File database = new File(props.databasePath());
        if (!database.isFile()) {
            log.warn("GeoIP database not found at {}, sessions will show no location", props.databasePath());
            return new NoopIpLocator();
        }
        return new MaxMindIpLocator(new DatabaseReader.Builder(database).build());
    }
}
