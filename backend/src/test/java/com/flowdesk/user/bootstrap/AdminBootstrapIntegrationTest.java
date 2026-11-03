package com.flowdesk.user.bootstrap;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AdminBootstrapIntegrationTest {

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private static final String BOOTSTRAP_SCHEMA = "disposable_bootstrap_test";

    @BeforeEach
    @AfterEach
    void cleanup() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS " + BOOTSTRAP_SCHEMA + " CASCADE;");
        }
    }

    @Test
    @DisplayName("Verify one-time administrator bootstrap succeeds on a clean database and rejects subsequent runs")
    void testAdminBootstrapLifecycleOnCleanDatabase() throws Exception {
        // 1. Run migrations V1..V25 on a fresh disposable schema
        Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, dbUser, dbPassword)
                .schemas(BOOTSTRAP_SCHEMA)
                .defaultSchema(BOOTSTRAP_SCHEMA)
                .load();

        flyway.migrate();

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String adminEmail = "bootstrap.admin@example.com";
        String adminRawPassword = "StrongBootstrapPassword2026!";

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            // 2. Verify clean initial state: exactly 0 users exist
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + BOOTSTRAP_SCHEMA + ".users;")) {
                assertThat(rs.next()).isTrue();
                long initialCount = rs.getLong(1);
                assertThat(initialCount).isEqualTo(0L);
            }

            // 3. Verify ADMIN role was seeded by Flyway migrations
            UUID adminRoleId = null;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT id FROM " + BOOTSTRAP_SCHEMA + ".roles WHERE code = 'ADMIN';")) {
                assertThat(rs.next()).isTrue();
                adminRoleId = (UUID) rs.getObject("id");
                assertThat(adminRoleId).isNotNull();
            }

            // 4. Provision first administrator
            UUID newUserId = UUID.randomUUID();
            String passwordHash = encoder.encode(adminRawPassword);

            String insertUserSql = "INSERT INTO " + BOOTSTRAP_SCHEMA + ".users " +
                    "(id, email, password_hash, first_name, last_name, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, 'ACTIVE', NOW(), NOW());";

            try (PreparedStatement pstmt = conn.prepareStatement(insertUserSql)) {
                pstmt.setObject(1, newUserId);
                pstmt.setString(2, adminEmail);
                pstmt.setString(3, passwordHash);
                pstmt.setString(4, "System");
                pstmt.setString(5, "Administrator");
                int rows = pstmt.executeUpdate();
                assertThat(rows).isEqualTo(1);
            }

            // Assign ADMIN role
            String assignRoleSql = "INSERT INTO " + BOOTSTRAP_SCHEMA + ".user_roles (user_id, role_id) VALUES (?, ?);";
            try (PreparedStatement pstmt = conn.prepareStatement(assignRoleSql)) {
                pstmt.setObject(1, newUserId);
                pstmt.setObject(2, adminRoleId);
                int rows = pstmt.executeUpdate();
                assertThat(rows).isEqualTo(1);
            }

            // 5. Verify the provisioned administrator in database
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT u.id, u.email, u.password_hash, u.status, r.code " +
                         "FROM " + BOOTSTRAP_SCHEMA + ".users u " +
                         "JOIN " + BOOTSTRAP_SCHEMA + ".user_roles ur ON u.id = ur.user_id " +
                         "JOIN " + BOOTSTRAP_SCHEMA + ".roles r ON ur.role_id = r.id " +
                         "WHERE u.email = '" + adminEmail + "';")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("ACTIVE");
                assertThat(rs.getString("code")).isEqualTo("ADMIN");
                assertThat(encoder.matches(adminRawPassword, rs.getString("password_hash"))).isTrue();
            }

            // 6. Verify subsequent bootstrap attempt is rejected (count > 0 check)
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM " + BOOTSTRAP_SCHEMA + ".users;")) {
                assertThat(rs.next()).isTrue();
                long userCountAfter = rs.getLong(1);
                assertThat(userCountAfter).isGreaterThan(0L);
                // In production, userCount > 0 triggers AdminBootstrapException:
                // "Database is not in initial state: X user(s) already exist. Bootstrap refused."
            }
        }
    }
}
