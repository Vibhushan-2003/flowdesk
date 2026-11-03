package com.flowdesk.config;

import com.flowdesk.attachment.service.TicketAttachmentService;
import com.flowdesk.attachment.storage.AttachmentStorageProperties;
import com.flowdesk.auth.config.RefreshTokenProperties;
import com.flowdesk.auth.ratelimit.RateLimiterProperties;
import com.flowdesk.user.bootstrap.AdminBootstrapRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("render")
@TestPropertySource(properties = {
        "spring.datasource.url=${DB_TEST_URL:jdbc:postgresql://localhost:5432/flowdesk_test}",
        "spring.datasource.username=${DB_TEST_USERNAME:${DB_USERNAME:flowdesk_app}}",
        "spring.datasource.password=${DB_TEST_PASSWORD:${DB_PASSWORD:12345678}}",
        "app.security.jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
        "FRONTEND_ORIGIN=https://flowdesk-demo.onrender.com",
        "FLOWDESK_FRONTEND_ORIGIN=https://flowdesk-demo.onrender.com",
        "app.cors.allowed-origin=https://flowdesk-demo.onrender.com",
        "app.security.password-reset.frontend-origin=https://flowdesk-demo.onrender.com",
        "app.security.invitation.frontend-origin=https://flowdesk-demo.onrender.com",
        "app.mail.enabled=false",
        "GEMINI_API_KEY=",
        "GOOGLE_OAUTH_ENABLED=false"
})
class RenderProfileStartupTest {

    @Autowired
    private Environment environment;

    @Autowired
    private AttachmentStorageProperties attachmentStorageProperties;

    @Autowired
    private RefreshTokenProperties refreshTokenProperties;

    @Autowired
    private RateLimiterProperties rateLimiterProperties;

    @Autowired
    private TicketAttachmentService ticketAttachmentService;

    @Autowired
    private AdminBootstrapRunner adminBootstrapRunner;

    @Test
    @DisplayName("Render profile loads successfully and enforces demo security and resource constraints")
    void renderProfileEnforcesConstraints() {
        assertNotNull(environment);
        assertTrue(environment.matchesProfiles("render"), "render profile must be active");

        // HikariCP conservative sizing for 512MB RAM and Render Free PostgreSQL
        int maxPoolSize = Integer.parseInt(environment.getProperty("spring.datasource.hikari.maximum-pool-size", "4"));
        int minIdle = Integer.parseInt(environment.getProperty("spring.datasource.hikari.minimum-idle", "1"));
        assertTrue(maxPoolSize <= 4, "Maximum pool size must be conservative (<= 4) for Render Free container");
        assertTrue(minIdle <= 1, "Minimum idle connections must be conservative (<= 1) for Render Free container");

        // Reverse proxy headers trusted for Render ingress
        assertTrue(rateLimiterProperties.isTrustProxyHeaders(), "trustProxyHeaders must be true in render profile");

        // Cross-site cookie attributes for separated frontend and backend subdomains
        assertEquals("None", refreshTokenProperties.getSameSite(), "Refresh cookie SameSite must be None for cross-site static site");
        assertTrue(refreshTokenProperties.isCookieSecure(), "Refresh cookie must be Secure");

        // Attachment uploads must be explicitly disabled (fail-closed, no unverified uploads on ephemeral disk)
        assertFalse(attachmentStorageProperties.isUploadEnabled(), "Attachment upload must be disabled in render demo profile");

        // Actuator lockdown: only health exposed, Prometheus disabled to prevent metric/route leakage
        assertEquals("health", environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("false", environment.getProperty("management.endpoint.prometheus.enabled"));
        assertEquals("never", environment.getProperty("management.endpoint.health.show-details"));

        // Dynamic port defaults to 8080 when PORT env var is unset
        assertEquals("8080", environment.getProperty("server.port"));

        // Graceful shutdown configured
        assertEquals("graceful", environment.getProperty("server.shutdown"));
    }

    @Test
    @DisplayName("Attachment upload attempts are rejected with HTTP 503 SERVICE_UNAVAILABLE in Render demo profile")
    void attachmentUploadIsRejectedWithServiceUnavailable() {
        UUID dummyUserId = UUID.randomUUID();
        ByteArrayInputStream dummyStream = new ByteArrayInputStream("test content".getBytes());

        assertThatThrownBy(() -> ticketAttachmentService.uploadAttachmentForEmployee(
                dummyUserId,
                "FD-1001",
                "test.txt",
                "text/plain",
                dummyStream,
                12L
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(rse.getReason()).contains("Attachment uploads are disabled in temporary demo environment");
                });

        ByteArrayInputStream dummyStreamSupport = new ByteArrayInputStream("support content".getBytes());
        assertThatThrownBy(() -> ticketAttachmentService.uploadAttachmentForSupport(
                dummyUserId,
                "FD-1001",
                "support.txt",
                "text/plain",
                dummyStreamSupport,
                15L
        ))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(rse.getReason()).contains("Attachment uploads are disabled in temporary demo environment");
                });
    }

    @Test
    @DisplayName("In-service bootstrap is idempotent and does not terminate the web application when executed")
    void inServiceBootstrapDoesNotTerminateApplication() {
        // Calling runner.run with empty args simulates normal web service startup.
        // It must not call System.exit or throw an unexpected exception.
        assertDoesNotThrow(() -> adminBootstrapRunner.run(new DefaultApplicationArguments(new String[]{})));
    }
}
