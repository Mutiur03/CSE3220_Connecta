package com.connecta.backend.inbox.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The V202611241300 migration against real PostgreSQL, on its own container: the schema is built up to the
 * version before it, rows are inserted, then the migration runs. Covers the backfill rules and the CHECK that
 * keeps an account on the matching channel. Never touches the development database.
 */
@Testcontainers(disabledWithoutDocker = true)
class ConversationAccountMigrationIntegrationTests {

    private static final String BEFORE = "202611241200";
    private static final String MIGRATIONS = "classpath:db/migration";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private Connection db;

    @BeforeEach
    void freshSchemaAtTheVersionBeforeTheMigration() throws SQLException {
        Flyway before = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .cleanDisabled(false).locations(MIGRATIONS).target(BEFORE).load();
        before.clean();
        before.migrate();
        db = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private void migrate() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(MIGRATIONS).load().migrate();
    }

    private void exec(String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }

    private String query(String sql) throws SQLException {
        try (Statement statement = db.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private UUID organization() throws SQLException {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO organizations (id, name, slug) VALUES ('" + id + "', 'Org', 'org-" + id + "')");
        return id;
    }

    private UUID account(UUID organization, String platform, String externalId) throws SQLException {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO meta_connected_accounts (id, organization_id, platform, external_account_id, "
                + "access_token_encrypted) VALUES ('" + id + "', '" + organization + "', '" + platform + "', '"
                + externalId + "', 'token')");
        return id;
    }

    private UUID page(UUID organization, String pageId) throws SQLException {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO meta_connected_pages (id, organization_id, page_id, page_name, "
                + "page_access_token_encrypted) VALUES ('" + id + "', '" + organization + "', '" + pageId
                + "', 'Page', 'token')");
        return id;
    }

    private UUID conversation(UUID organization, String channel) throws SQLException {
        UUID id = UUID.randomUUID();
        exec("INSERT INTO conversations (id, organization_id, channel, external_thread_id) VALUES ('" + id
                + "', '" + organization + "', '" + channel + "', 'thread-" + id + "')");
        return id;
    }

    private String column(String column, UUID conversation) throws SQLException {
        return query("SELECT " + column + " FROM conversations WHERE id = '" + conversation + "'");
    }

    @Nested
    class Backfill {

        @Test
        void anOrganizationWithExactlyOneAccountOfTheChannelIsBackfilled() throws SQLException {
            UUID org = organization();
            UUID account = account(org, "INSTAGRAM", "ig-1");
            UUID page = page(org, "page-1");
            UUID instagram = conversation(org, "INSTAGRAM");
            UUID messenger = conversation(org, "MESSENGER");

            migrate();

            assertEquals(account.toString(), column("instagram_account_id", instagram));
            assertNull(column("meta_page_id", instagram));
            assertEquals(page.toString(), column("meta_page_id", messenger));
            assertNull(column("instagram_account_id", messenger));
        }

        @Test
        void anOrganizationWithTwoAccountsOfTheChannelStaysUnlinked() throws SQLException {
            UUID org = organization();
            account(org, "INSTAGRAM", "ig-1");
            account(org, "INSTAGRAM", "ig-2");
            page(org, "page-1");
            page(org, "page-2");
            UUID instagram = conversation(org, "INSTAGRAM");
            UUID messenger = conversation(org, "MESSENGER");

            migrate();

            assertNull(column("instagram_account_id", instagram));
            assertNull(column("meta_page_id", messenger));
        }

        @Test
        void anotherOrganizationsAccountIsNeverUsed() throws SQLException {
            UUID org = organization();
            account(organization(), "INSTAGRAM", "ig-other");
            UUID instagram = conversation(org, "INSTAGRAM");

            migrate();

            assertNull(column("instagram_account_id", instagram));
        }

        @Test
        void onlyAnInstagramPlatformAccountIsMatched() throws SQLException {
            exec("ALTER TABLE meta_connected_accounts DROP CONSTRAINT meta_connected_accounts_platform_known");
            UUID org = organization();
            UUID instagramAccount = account(org, "INSTAGRAM", "ig-1");
            account(org, "OTHER", "x-1");
            UUID withOtherOnly = organization();
            account(withOtherOnly, "OTHER", "x-2");
            UUID instagram = conversation(org, "INSTAGRAM");
            UUID unmatched = conversation(withOtherOnly, "INSTAGRAM");

            migrate();

            assertEquals(instagramAccount.toString(), column("instagram_account_id", instagram));
            assertNull(column("instagram_account_id", unmatched));
        }
    }

    @Nested
    class AccountMatchesChannelConstraint {

        @Test
        void aMessengerConversationCannotPointAtAnInstagramAccount() throws SQLException {
            UUID org = organization();
            UUID account = account(org, "INSTAGRAM", "ig-1");
            UUID messenger = conversation(org, "MESSENGER");
            migrate();

            assertThrows(SQLException.class, () -> exec("UPDATE conversations SET instagram_account_id = '"
                    + account + "' WHERE id = '" + messenger + "'"));
        }

        @Test
        void anInstagramConversationCannotPointAtAPage() throws SQLException {
            UUID org = organization();
            UUID page = page(org, "page-1");
            UUID instagram = conversation(org, "INSTAGRAM");
            migrate();

            assertThrows(SQLException.class, () -> exec("UPDATE conversations SET meta_page_id = '" + page
                    + "' WHERE id = '" + instagram + "'"));
        }

        @Test
        void deletingTheAccountKeepsTheConversationAndNullsTheLink() throws SQLException {
            UUID org = organization();
            UUID account = account(org, "INSTAGRAM", "ig-1");
            UUID instagram = conversation(org, "INSTAGRAM");
            migrate();
            assertEquals(account.toString(), column("instagram_account_id", instagram));

            exec("DELETE FROM meta_connected_accounts WHERE id = '" + account + "'");

            assertNull(column("instagram_account_id", instagram));
            assertEquals("INSTAGRAM", column("channel", instagram));
        }
    }
}
