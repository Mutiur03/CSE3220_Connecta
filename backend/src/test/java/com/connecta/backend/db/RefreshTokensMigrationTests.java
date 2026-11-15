package com.connecta.backend.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.auth.domain.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

// No database is started: this checks the migration text against the entity mapping.
class RefreshTokensMigrationTests {

    private static String sql() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V202611152010__refresh_tokens.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    @Test
    void createsTheTableNamedByTheEntity() throws Exception {
        String table = RefreshToken.class.getAnnotation(Table.class).name();

        assertTrue(sql().contains("create table " + table + " ("));
    }

    @Test
    void definesEveryMappedColumn() throws Exception {
        String sql = sql();
        for (Field f : RefreshToken.class.getDeclaredFields()) {
            Column column = f.getAnnotation(Column.class);
            if (column != null) {
                assertTrue(sql.contains(column.name()), "missing column " + column.name());
            }
        }
    }

    @Test
    void storesAUniqueHashAndNoRawToken() throws Exception {
        String sql = sql();

        assertTrue(sql.contains("token_hash char(64) not null unique"));
        assertFalse(sql.contains("raw_token"));
    }

    @Test
    void sessionsAreRemovedWithTheirUser() throws Exception {
        assertTrue(sql().contains("references users (id) on delete cascade"));
    }
}
