package com.flowdesk.backend;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final DataSource dataSource;

    @Autowired
    public HealthController(@Autowired(required = false) DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Application Liveness Probe.
     * Validates that the Spring Boot web container, DispatcherServlet,
     * and security filter chains are initialized and responsive.
     * Retains exact backward compatibility with CI and smoke tests.
     */
    @GetMapping("/api/health")
    public String health() {
        return "Backend is running!";
    }

    /**
     * Application & Database Readiness Probe.
     * Validates that the database connection pool is active and capable of
     * executing relational transactions. Suppresses all sensitive details.
     */
    @GetMapping("/api/health/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        if (dataSource == null) {
            log.warn("Readiness probe called but DataSource is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "UNAVAILABLE"));
        }

        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                return ResponseEntity.ok(Map.of("status", "READY"));
            } else {
                log.warn("Database connection check failed isValid(2) probe");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("status", "DEGRADED"));
            }
        } catch (Exception ex) {
            log.error("Database readiness check failed: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "UNAVAILABLE"));
        }
    }
}
