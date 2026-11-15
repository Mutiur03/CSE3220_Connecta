package com.connecta.backend.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.connecta.backend.user.domain.Role;
import com.connecta.backend.user.domain.Roles;
import com.connecta.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

// No database is started: this checks the migration text against the entity mappings.
class UsersAndRolesMigrationTests {

    private static String sql() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V202611152000__users_and_roles.sql"))
                .toLowerCase(Locale.ROOT);
    }

    @Test
    void createsTablesNamedByTheEntities() throws Exception {
        String sql = sql();
        for (Class<?> entity : new Class<?>[] {User.class, Role.class}) {
            String table = entity.getAnnotation(Table.class).name();
            assertTrue(sql.contains("create table " + table + " ("), "missing table " + table);
        }
        assertTrue(sql.contains("create table user_roles ("));
    }

    @Test
    void definesEveryMappedColumn() throws Exception {
        String sql = sql();
        for (Class<?> entity : new Class<?>[] {User.class, Role.class}) {
            for (Field f : entity.getDeclaredFields()) {
                Column column = f.getAnnotation(Column.class);
                if (column != null && !column.name().isEmpty()) {
                    assertTrue(sql.contains(column.name()), "missing column " + column.name());
                }
            }
        }
    }

    @Test
    void emailsAreUniqueIgnoringCase() throws Exception {
        assertTrue(sql().contains("create unique index users_email_lower_key on users (lower(email))"));
    }

    @Test
    void seedsTheKnownRoles() throws Exception {
        String sql = sql();
        for (String role : new String[] {Roles.OWNER, Roles.ADMIN, Roles.AGENT}) {
            assertTrue(sql.contains("'" + role.toLowerCase(Locale.ROOT) + "'"), "role not seeded: " + role);
        }
    }
}
