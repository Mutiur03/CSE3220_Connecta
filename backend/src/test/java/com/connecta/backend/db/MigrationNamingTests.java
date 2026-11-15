package com.connecta.backend.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

// Timestamp versions keep parallel branches from colliding on a version number (see AGENTS.md).
class MigrationNamingTests {

    private static final Pattern NAME = Pattern.compile("V\\d{12}__[a-z0-9_]+\\.sql");

    @Test
    void migrationsUseTimestampVersions() {
        File[] files = new File("src/main/resources/db/migration").listFiles();
        assertTrue(files != null && files.length > 0, "no migrations found");
        for (File f : files) {
            assertTrue(NAME.matcher(f.getName()).matches(), "bad migration name: " + f.getName());
        }
    }
}
