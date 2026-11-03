package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.ratelimit.LoginRateLimitingFilter;
import com.flowdesk.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration tests validating real embedded Tomcat RemoteIpValve
 * and Spring Security filter interaction over actual TCP HTTP loopback connections.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.remote-ip-header=x-forwarded-for",
        "server.tomcat.remoteip.protocol-header=x-forwarded-proto",
        "server.tomcat.remoteip.internal-proxies=127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|::1|0:0:0:0:0:0:0:1",
        "app.security.rate-limit.login.enabled=true",
        "app.security.rate-limit.login.trust-proxy-headers=true"
})
class EmbeddedTomcatRemoteIpIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @MockitoBean
    private AuthService authService;

    @Autowired
    private LoginRateLimitingFilter loginRateLimitingFilter;

    @BeforeEach
    void setUp() {
        if (loginRateLimitingFilter != null) {
            if (loginRateLimitingFilter.getIpLimiter() != null) {
                loginRateLimitingFilter.getIpLimiter().reset();
            }
            if (loginRateLimitingFilter.getIpEmailLimiter() != null) {
                loginRateLimitingFilter.getIpEmailLimiter().reset();
            }
        }
    }

    private String getLoginUrl() {
        return "http://127.0.0.1:" + port + "/api/auth/login";
    }

    @Test
    @DisplayName("Tomcat RemoteIpValve: Distinct external IPs forwarded by loopback proxy get independent rate-limit buckets")
    void distinctForwardedIpsGetIndependentBucketsOverRealTomcat() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Invalid credentials")
        );

        String clientA = "203.0.113.111";
        String clientB = "203.0.113.222";
        String payload = "{\"email\":\"target@flowdesk.test\",\"password\":\"wrongpass\"}";

        // Client A makes 5 failed login attempts through loopback proxy
        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getLoginUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", clientA)
                    .header("X-Forwarded-Proto", "https")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode(),
                    "Attempt " + (i + 1) + " for Client A should be 401 UNAUTHORIZED");
        }

        // 6th attempt for Client A should be rate-limited (HTTP 429)
        HttpRequest requestBlocked = HttpRequest.newBuilder()
                .uri(URI.create(getLoginUrl()))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientA)
                .header("X-Forwarded-Proto", "https")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> responseBlocked = httpClient.send(requestBlocked, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, responseBlocked.statusCode(), "Client A should be blocked with HTTP 429");
        assertTrue(responseBlocked.headers().firstValue("Retry-After").isPresent(), "Should include Retry-After header");

        // Client B simultaneously attempts login with different IP through same proxy
        HttpRequest requestB = HttpRequest.newBuilder()
                .uri(URI.create(getLoginUrl()))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientB)
                .header("X-Forwarded-Proto", "https")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> responseB = httpClient.send(requestB, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, responseB.statusCode(),
                "Client B must receive 401 UNAUTHORIZED in their own independent rate-limit bucket");
    }

    @Test
    @DisplayName("Tomcat RemoteIpValve: Forged X-Forwarded-For prefix cannot bypass rate limit (right-to-left resolution)")
    void forgedPrefixCannotBypassRateLimitOverRealTomcat() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Invalid credentials")
        );

        String genuineClient = "198.51.100.77";
        String payload = "{\"email\":\"target@flowdesk.test\",\"password\":\"wrongpass\"}";

        // Client sends varying spoofed prefixes, but proxy appends genuine client at end
        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getLoginUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", "1.1.1." + i + ", " + genuineClient)
                    .header("X-Forwarded-Proto", "https")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }

        // 6th attempt with another spoofed prefix must still be blocked because genuine IP is exhausted
        HttpRequest blockedRequest = HttpRequest.newBuilder()
                .uri(URI.create(getLoginUrl()))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", "9.9.9.9, " + genuineClient)
                .header("X-Forwarded-Proto", "https")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> blockedResponse = httpClient.send(blockedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, blockedResponse.statusCode(),
                "Forged prefix cannot bypass rate limit when genuine client IP is parsed right-to-left");
    }

    @Test
    @DisplayName("Tomcat RemoteIpValve: IPv6 client address in X-Forwarded-For is properly extracted and rate-limited")
    void ipv6ClientAddressRateLimitedOverRealTomcat() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Invalid credentials")
        );

        String ipv6Client = "2001:db8:acad::1";
        String payload = "{\"email\":\"ipv6user@flowdesk.test\",\"password\":\"wrongpass\"}";

        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getLoginUrl()))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", ipv6Client)
                    .header("X-Forwarded-Proto", "https")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(401, response.statusCode());
        }

        HttpRequest blockedRequest = HttpRequest.newBuilder()
                .uri(URI.create(getLoginUrl()))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ipv6Client)
                .header("X-Forwarded-Proto", "https")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> blockedResponse = httpClient.send(blockedRequest, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, blockedResponse.statusCode(),
                "IPv6 client should be rate-limited after 5 attempts");
    }
}
