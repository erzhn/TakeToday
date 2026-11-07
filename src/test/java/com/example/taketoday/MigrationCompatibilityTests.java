package com.example.taketoday;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Проверяет чистую установку и обновление БД со старой V11 optional two factor. */
class MigrationCompatibilityTests {
    private final String url = System.getProperty("migration.test.url",
            "jdbc:h2:mem:migrations;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    private final String user = System.getProperty("migration.test.user", "sa");
    private final String password = System.getProperty("migration.test.password", "");

    @Test
    void freshDatabaseMigratesThroughVersion13() throws Exception {
        inIsolatedSchema(schema -> {
            var flyway = flyway(schema, null);
            assertEquals(13, flyway.migrate().migrationsExecuted);
            flyway.validate();
            assertEquals("13", flyway.info().current().getVersion().getVersion());
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var db = connection(schema); var st = db.createStatement()) {
                st.executeQuery("SELECT two_factor_enabled, blocked_reason FROM accounts").close();
                st.executeQuery("SELECT archived, hidden_reason FROM offers").close();
                st.executeQuery("SELECT provider_refund_id FROM refunds").close();
            }
        });
    }

    @Test
    void existingOptionalTwoFactorDatabaseUpgradesWithoutLosingAccounts() throws Exception {
        inIsolatedSchema(schema -> {
            var old = flyway(schema, "11");
            assertEquals(11, old.migrate().migrationsExecuted);
            // Checksum реально применённой V11 из прежней ветки: содержимое должно остаться прежним.
            assertEquals(-2026218794, old.info().current().getChecksum());
            var account = UUID.randomUUID();
            try (var db = connection(schema); var st = db.prepareStatement("""
                    INSERT INTO accounts (id, email, password_hash, name, role, two_factor_enabled)
                    VALUES (?, 'migration@example.invalid', 'test', 'Migration', 'CUSTOMER', TRUE)
                    """)) {
                st.setObject(1, account);
                st.executeUpdate();
            }
            var updated = flyway(schema, null);
            assertEquals(2, updated.migrate().migrationsExecuted);
            updated.validate();
            try (var db = connection(schema); var st = db.prepareStatement(
                    "SELECT two_factor_enabled FROM accounts WHERE id = ?")) {
                st.setObject(1, account);
                try (var rows = st.executeQuery()) {
                    assertTrue(rows.next());
                    assertTrue(rows.getBoolean(1));
                }
            }
        });
    }

    private Flyway flyway(String schema, String target) {
        var config = Flyway.configure().dataSource(url, user, password)
                .locations("classpath:db/migration").schemas(schema).defaultSchema(schema);
        if (target != null) config.target(target);
        return config.load();
    }

    private Connection connection(String schema) throws Exception {
        var db = DriverManager.getConnection(url, user, password);
        db.setSchema(schema);
        return db;
    }

    private void inIsolatedSchema(SchemaCheck check) throws Exception {
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            check.run(schema);
        } finally {
            // Удаляется только уникальная схема, созданная этим тестом, не public и не сама БД.
            try (var db = DriverManager.getConnection(url, user, password); var st = db.createStatement()) {
                st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @FunctionalInterface
    private interface SchemaCheck {
        void run(String schema) throws Exception;
    }
}
