package com.connecta.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileReader;
import java.io.Reader;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class EnvSettingsTests {

    private static Properties load(String name) throws Exception {
        Properties p = new Properties();
        try (Reader r = new FileReader("src/main/resources/" + name)) {
            p.load(r);
        }
        return p;
    }

    @Test
    void baseHasNoCredentialDefaults() throws Exception {
        Properties base = load("application.properties");
        for (String key : new String[] {"url", "username", "password"}) {
            assertEquals("${DB_" + (key.equals("username") ? "USER" : key.toUpperCase()) + "}",
                    base.getProperty("spring.datasource." + key));
        }
        assertEquals("dev", base.getProperty("spring.profiles.default"));
    }

    @Test
    void devProvidesLocalDefaultsAndProdProvidesNone() throws Exception {
        assertTrue(load("application-dev.properties").getProperty("spring.datasource.url").contains(":jdbc:postgresql://localhost"));
        assertFalse(load("application-prod.properties").stringPropertyNames().stream()
                .anyMatch(k -> k.startsWith("spring.datasource")));
    }
}
