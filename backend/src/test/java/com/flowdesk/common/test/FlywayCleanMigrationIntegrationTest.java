package com.flowdesk.common.test;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlywayCleanMigrationIntegrationTest {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private static final String DISPOSABLE_SCHEMA = "disposable_val";

    @BeforeEach
    @AfterEach
    void cleanup() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + DISPOSABLE_SCHEMA + " CASCADE;");
        }
    }

    @Test
    @DisplayName("Verify clean Flyway V1 through V23 migrations on a brand new disposable schema")
    void testCleanV1ToV23MigrationsOnFreshSchema() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, dbUser, dbPassword)
                .schemas(DISPOSABLE_SCHEMA)
                .defaultSchema(DISPOSABLE_SCHEMA)
                .locations("classpath:db/migration")
                .load();

        // 1. Run migrations from scratch (V1 to V23)
        MigrateResult result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion).isEqualTo("23");
        assertThat(result.migrationsExecuted).isEqualTo(23);

        // 2. Validate all checksums on fresh schema
        flyway.validate();

        // 3. Inspect schema structure in disposable schema
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement stmt = conn.createStatement()) {

            // Check refresh_sessions table exists
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, data_type, is_nullable " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = '" + DISPOSABLE_SCHEMA + "' AND table_name = 'refresh_sessions'")) {
                boolean hasTokenHash = false;
                boolean hasFamilyId = false;
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    if ("token_hash".equals(col)) hasTokenHash = true;
                    if ("family_id".equals(col)) hasFamilyId = true;
                }
                assertThat(hasTokenHash).isTrue();
                assertThat(hasFamilyId).isTrue();
            }

            // Check ticket_attachments table has scan_status with PENDING_SCAN default
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, column_default " +
                    "FROM information_schema.columns " +
                    "WHERE table_schema = '" + DISPOSABLE_SCHEMA + "' AND table_name = 'ticket_attachments' AND column_name = 'scan_status'")) {
                assertThat(rs.next()).isTrue();
                String colDefault = rs.getString("column_default");
                assertThat(colDefault).contains("PENDING_SCAN");
            }
        }
    }
}
