package com.flowdesk.auth.ratelimit;

import com.flowdesk.auth.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitingFilterTest {

    @Mock
    private SecurityErrorResponseWriter errorResponseWriter;

    @Mock
    private FilterChain filterChain;

    private JsonMapper jsonMapper;
    private RateLimiterProperties properties;
    private LoginRateLimitingFilter filter;

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advanceSeconds(long seconds) {
            this.now = this.now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        properties.setIpMaxAttempts(3);
        properties.setIpWindowSeconds(60);
        properties.setIpEmailMaxAttempts(2);
        properties.setIpEmailWindowSeconds(300);
        properties.setMaxTrackedKeys(1000);
        properties.setTrustProxyHeaders(false);

        clock = new MutableClock(Instant.parse("2026-10-24T12:00:00Z"));
        filter = new LoginRateLimitingFilter(properties, errorResponseWriter, jsonMapper, clock);
        filter.init();
    }

    @Test
    @DisplayName("Should pass non-login requests through immediately without rate limiting")
    void nonLoginEndpointsPassThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tickets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(errorResponseWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should allow requests within quota and preserve body for downstream AuthController")
    void allowWithinQuota() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("192.168.1.10");
        request.setContent("{\"email\":\"USER@EXAMPLE.COM\",\"password\":\"secret123\"}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(errorResponseWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should block with 429 and Retry-After when IP+Email quota exceeded")
    void blockWhenIpEmailQuotaExceeded() throws ServletException, IOException {
        String emailJson = "{\"email\":\"admin@flowdesk.test\",\"password\":\"secret\"}";

        // Attempt 1 allowed
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr("10.0.0.5");
        req1.setContent(emailJson.getBytes());
        filter.doFilter(req1, new MockHttpServletResponse(), filterChain);

        // Attempt 2 allowed (max is 2)
        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr("10.0.0.5");
        req2.setContent(emailJson.getBytes());
        filter.doFilter(req2, new MockHttpServletResponse(), filterChain);

        // Attempt 3 blocked
        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", "/api/auth/login");
        req3.setRemoteAddr("10.0.0.5");
        req3.setContent(emailJson.getBytes());
        MockHttpServletResponse resp3 = new MockHttpServletResponse();

        filter.doFilter(req3, resp3, filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
        verify(errorResponseWriter).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), any());
        assertNotNull(resp3.getHeader("Retry-After"));
        assertTrue(resp3.getHeader("Cache-Control").contains("no-store"));
    }

    @Test
    @DisplayName("Should block with 429 when IP quota is exceeded across different emails")
    void blockWhenOverallIpQuotaExceeded() throws ServletException, IOException {
        // IP max is 3 attempts
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.99");
            req.setContent(("{\"email\":\"user" + i + "@flowdesk.test\",\"password\":\"pass\"}").getBytes());
            filter.doFilter(req, new MockHttpServletResponse(), filterChain);
        }

        // 4th request from same IP with yet another email is blocked by IP limit
        MockHttpServletRequest req4 = new MockHttpServletRequest("POST", "/api/auth/login");
        req4.setRemoteAddr("10.0.0.99");
        req4.setContent("{\"email\":\"fresh@flowdesk.test\",\"password\":\"pass\"}".getBytes());
        MockHttpServletResponse resp4 = new MockHttpServletResponse();

        filter.doFilter(req4, resp4, filterChain);

        verify(filterChain, times(3)).doFilter(any(), any());
        verify(errorResponseWriter).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), any());
        assertNotNull(resp4.getHeader("Retry-After"));
    }

    @Test
    @DisplayName("Should normalize email case and whitespace for quota key")
    void normalizeEmailForQuota() throws ServletException, IOException {
        // "  Admin@FlowDesk.Test  " and "admin@flowdesk.test" share the same quota
        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr("10.0.0.2");
        req1.setContent("{\"email\":\"  Admin@FlowDesk.Test  \",\"password\":\"p\"}".getBytes());
        filter.doFilter(req1, new MockHttpServletResponse(), filterChain);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr("10.0.0.2");
        req2.setContent("{\"email\":\"admin@flowdesk.test\",\"password\":\"p\"}".getBytes());
        filter.doFilter(req2, new MockHttpServletResponse(), filterChain);

        // 3rd attempt blocked
        MockHttpServletRequest req3 = new MockHttpServletRequest("POST", "/api/auth/login");
        req3.setRemoteAddr("10.0.0.2");
        req3.setContent("{\"email\":\"ADMIN@FLOWDESK.TEST\",\"password\":\"p\"}".getBytes());
        MockHttpServletResponse resp3 = new MockHttpServletResponse();
        filter.doFilter(req3, resp3, filterChain);

        verify(filterChain, times(2)).doFilter(any(), any());
        verify(errorResponseWriter).write(any(), any(), eq(HttpStatus.TOO_MANY_REQUESTS), any());
    }

    @Test
    @DisplayName("Should bypass filtering when enabled is false")
    void bypassWhenDisabled() throws ServletException, IOException {
        properties.setEnabled(false);

        for (int i = 0; i < 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("1.2.3.4");
            req.setContent("{\"email\":\"a@b.com\",\"password\":\"p\"}".getBytes());
            filter.doFilter(req, new MockHttpServletResponse(), filterChain);
        }

        verify(filterChain, times(5)).doFilter(any(), any());
        verify(errorResponseWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject oversized login request body with 413 Payload Too Large before parsing")
    void rejectOversizedBody() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("10.0.0.88");
        // maxBodyBytes is 8192 by default; send 9000 bytes
        byte[] largePayload = new byte[9000];
        req.setContent(largePayload);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        // Filter chain must never be invoked
        verify(filterChain, never()).doFilter(any(), any());
        // Must write 413 Payload Too Large
        verify(errorResponseWriter).write(any(), any(), eq(HttpStatus.PAYLOAD_TOO_LARGE), eq("Request payload exceeds maximum allowed size."));
    }

    @Test
    @DisplayName("Should reject request immediately if Content-Length header exceeds maxBodyBytes")
    void rejectOversizedContentLength() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("10.0.0.89");
        req.setContent(new byte[100]);
        req.addHeader("Content-Length", "20000");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        verify(filterChain, never()).doFilter(any(), any());
        verify(errorResponseWriter).write(any(), any(), eq(HttpStatus.PAYLOAD_TOO_LARGE), eq("Request payload exceeds maximum allowed size."));
    }

    @Test
    @DisplayName("Should safely handle malformed JSON without leaking error details or raw content")
    void handleMalformedJsonSafely() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("10.0.0.90");
        req.setContent("{malformed json here: password=Secret123".getBytes());
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, filterChain);

        // Request still passes through to downstream AuthController which will handle invalid JSON appropriately
        verify(filterChain).doFilter(any(), any());
    }
}

