package com.flowdesk.config;

import com.flowdesk.attachment.malware.ClamAvMalwareScanner;
import com.flowdesk.attachment.malware.MalwareScanner;
import com.flowdesk.auth.ratelimit.RateLimiterProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=${DB_TEST_URL:jdbc:postgresql://localhost:5432/flowdesk_test}",
        "spring.datasource.username=${DB_TEST_USERNAME:${DB_USERNAME:flowdesk_app}}",
        "spring.datasource.password=${DB_TEST_PASSWORD:${DB_PASSWORD:12345678}}",
        "app.security.jwt.secret=YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWE=",
        "FRONTEND_ORIGIN=https://flowdesk.test.internal",
        "FLOWDESK_FRONTEND_ORIGIN=https://flowdesk.test.internal",
        "app.cors.allowed-origin=https://flowdesk.test.internal",
        "app.security.password-reset.frontend-origin=https://flowdesk.test.internal",
        "app.security.invitation.frontend-origin=https://flowdesk.test.internal",
        "app.mail.enabled=false",
        "app.mail.host=smtp.test.internal",
        "app.attachment.malware-scanner.type=clamav",
        "app.attachment.malware-scanner.clamav.host=localhost",
        "app.attachment.malware-scanner.clamav.port=3310"
})
class ProductionProfileStartupTest {

    @Autowired
    private Environment environment;

    @Autowired
    private MalwareScanner malwareScanner;

    @Autowired
    private RateLimiterProperties rateLimiterProperties;

    @Test
    @DisplayName("Production profile loads successfully and enforces production security defaults")
    void productionProfileEnforcesSecurityDefaults() {
        assertNotNull(environment);
        assertTrue(environment.matchesProfiles("prod"), "prod profile must be active");

        // ClamAV scanner must be instantiated (mock/test disallowed in prod)
        assertNotNull(malwareScanner);
        assertTrue(malwareScanner instanceof ClamAvMalwareScanner, "Must use ClamAvMalwareScanner in prod profile");

        // Rate limiter must trust proxy headers in production profile
        assertTrue(rateLimiterProperties.isTrustProxyHeaders(), "trustProxyHeaders must be true in prod profile");

        // SQL logging must be disabled in production
        assertEquals("false", environment.getProperty("spring.jpa.show-sql"));

        // Error stack trace leakage must be disabled
        assertEquals("never", environment.getProperty("server.error.include-stacktrace"));
        assertEquals("never", environment.getProperty("server.error.include-message"));
        assertEquals("false", environment.getProperty("server.error.include-exception"));

        // Graceful shutdown must be configured
        assertEquals("graceful", environment.getProperty("server.shutdown"));
    }
}
