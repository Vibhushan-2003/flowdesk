package com.flowdesk.common.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestDatabaseSafetyGuardTest {

    @Test
    @DisplayName("extractDatabaseName correctly parses plain and parameterized JDBC URLs")
    void extractDatabaseName() {
        assertThat(TestDatabaseSafetyGuard.extractDatabaseName("jdbc:postgresql://localhost:5432/flowdesk"))
                .isEqualTo("flowdesk");

        assertThat(TestDatabaseSafetyGuard.extractDatabaseName("jdbc:postgresql://localhost:5432/flowdesk_test"))
                .isEqualTo("flowdesk_test");

        assertThat(TestDatabaseSafetyGuard.extractDatabaseName("jdbc:postgresql://localhost:5432/flowdesk_test?sslmode=disable&currentSchema=public"))
                .isEqualTo("flowdesk_test");

        assertThat(TestDatabaseSafetyGuard.extractDatabaseName("jdbc:postgresql://10.0.0.1:5432/custom_app_test"))
                .isEqualTo("custom_app_test");

        assertThat(TestDatabaseSafetyGuard.extractDatabaseName("")).isEqualTo("");
        assertThat(TestDatabaseSafetyGuard.extractDatabaseName(null)).isEqualTo("");
    }

    @Test
    @DisplayName("validateDatabaseTarget unconditionally blocks development database 'flowdesk'")
    void validateDatabaseTargetBlocksFlowdesk() {
        assertThatThrownBy(() ->
                TestDatabaseSafetyGuard.validateDatabaseTarget("jdbc:postgresql://localhost:5432/flowdesk"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL TEST DATA SAFETY VIOLATION")
                .hasMessageContaining("flowdesk");
    }

    @Test
    @DisplayName("validateDatabaseTarget unconditionally blocks database missing 'test' in name")
    void validateDatabaseTargetBlocksNonTestDatabase() {
        assertThatThrownBy(() ->
                TestDatabaseSafetyGuard.validateDatabaseTarget("jdbc:postgresql://localhost:5432/flowdesk_production"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST DATABASE SAFETY VIOLATION")
                .hasMessageContaining("flowdesk_production");
    }

    @Test
    @DisplayName("validateDatabaseTarget permits 'flowdesk_test' and valid test database names")
    void validateDatabaseTargetPermitsTestDatabase() {
        assertThatCode(() ->
                TestDatabaseSafetyGuard.validateDatabaseTarget("jdbc:postgresql://localhost:5432/flowdesk_test"))
                .doesNotThrowAnyException();

        assertThatCode(() ->
                TestDatabaseSafetyGuard.validateDatabaseTarget("jdbc:postgresql://localhost:5432/flowdesk_test?sslmode=disable"))
                .doesNotThrowAnyException();

        assertThatCode(() ->
                TestDatabaseSafetyGuard.validateDatabaseTarget("jdbc:postgresql://localhost:5432/flowdesk_ci_test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateLiveDataSource blocks live connection to 'flowdesk'")
    void validateLiveDataSourceBlocksFlowdesk() throws SQLException {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("SELECT current_database(), current_user")).thenReturn(mockRs);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getString(1)).thenReturn("flowdesk");
        when(mockRs.getString(2)).thenReturn("flowdesk_app");

        assertThatThrownBy(() -> TestDatabaseSafetyGuard.validateLiveDataSource(mockDs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL TEST DATA SAFETY VIOLATION")
                .hasMessageContaining("flowdesk");
    }

    @Test
    @DisplayName("validateLiveDataSource permits live connection to 'flowdesk_test'")
    void validateLiveDataSourcePermitsFlowdeskTest() throws SQLException {
        DataSource mockDs = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);
        Statement mockStmt = mock(Statement.class);
        ResultSet mockRs = mock(ResultSet.class);

        when(mockDs.getConnection()).thenReturn(mockConn);
        when(mockConn.createStatement()).thenReturn(mockStmt);
        when(mockStmt.executeQuery("SELECT current_database(), current_user")).thenReturn(mockRs);
        when(mockRs.next()).thenReturn(true);
        when(mockRs.getString(1)).thenReturn("flowdesk_test");
        when(mockRs.getString(2)).thenReturn("flowdesk_app");

        assertThatCode(() -> TestDatabaseSafetyGuard.validateLiveDataSource(mockDs))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TestDatabaseSafetyInitializer defaults to flowdesk_test")
    void initializerDefaultsToFlowdeskTest() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        StandardEnvironment env = new StandardEnvironment();
        ctx.setEnvironment(env);

        TestDatabaseSafetyInitializer initializer = new TestDatabaseSafetyInitializer();
        initializer.initialize(ctx);

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/flowdesk_test");
    }

    @Test
    @DisplayName("TestDatabaseSafetyInitializer honors explicit DB_TEST_URL")
    void initializerHonorsExplicitDbTestUrl() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("customTest", Map.of(
                "DB_TEST_URL", "jdbc:postgresql://localhost:5432/flowdesk_test_custom"
        )));
        ctx.setEnvironment(env);

        TestDatabaseSafetyInitializer initializer = new TestDatabaseSafetyInitializer();
        initializer.initialize(ctx);

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/flowdesk_test_custom");
    }

    @Test
    @DisplayName("TestDatabaseSafetyInitializer fails fast if DB_TEST_URL points to flowdesk")
    void initializerFailsFastIfDbTestUrlPointsToFlowdesk() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("unsafeTest", Map.of(
                "DB_TEST_URL", "jdbc:postgresql://localhost:5432/flowdesk"
        )));
        ctx.setEnvironment(env);

        TestDatabaseSafetyInitializer initializer = new TestDatabaseSafetyInitializer();
        assertThatThrownBy(() -> initializer.initialize(ctx))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CRITICAL TEST DATA SAFETY VIOLATION");
    }
}
