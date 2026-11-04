package com.flowdesk.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    @DisplayName("Liveness probe /api/health returns 200 with standard string")
    void healthReturnsStandardLivenessString() {
        DataSource mockDataSource = mock(DataSource.class);
        HealthController controller = new HealthController(mockDataSource);

        String result = controller.health();

        assertThat(result).isEqualTo("Backend is running!");
    }

    @Test
    @DisplayName("Readiness probe /api/health/ready returns 200 READY when database is healthy")
    void readinessReturnsOkWhenDatabaseHealthy() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);

        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.isValid(anyInt())).thenReturn(true);

        HealthController controller = new HealthController(mockDataSource);
        ResponseEntity<Map<String, String>> response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "READY");
    }

    @Test
    @DisplayName("Readiness probe /api/health/ready returns 503 DEGRADED when connection validation fails")
    void readinessReturnsDegradedWhenConnectionInvalid() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConn = mock(Connection.class);

        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.isValid(anyInt())).thenReturn(false);

        HealthController controller = new HealthController(mockDataSource);
        ResponseEntity<Map<String, String>> response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "DEGRADED");
    }

    @Test
    @DisplayName("Readiness probe /api/health/ready returns 503 UNAVAILABLE when database throws exception")
    void readinessReturnsUnavailableOnSqlException() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        HealthController controller = new HealthController(mockDataSource);
        ResponseEntity<Map<String, String>> response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "UNAVAILABLE");
        // Verify no sensitive exception or database details are leaked
        assertThat(response.getBody()).doesNotContainKey("error");
        assertThat(response.getBody()).doesNotContainKey("message");
    }

    @Test
    @DisplayName("Readiness probe /api/health/ready returns 503 UNAVAILABLE when DataSource is null")
    void readinessReturnsUnavailableWhenDataSourceNull() {
        HealthController controller = new HealthController(null);
        ResponseEntity<Map<String, String>> response = controller.readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("status", "UNAVAILABLE");
    }

    @Test
    @DisplayName("Version probe /api/health/version returns 200 with status UP and commit metadata")
    void versionReturnsOkWithMetadata() {
        HealthController controller = new HealthController(null);
        ResponseEntity<Map<String, String>> response = controller.version();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsKey("commit");
        assertThat(response.getBody()).containsKey("branch");
    }
}
