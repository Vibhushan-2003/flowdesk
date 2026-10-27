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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OAuthRateLimitingFilterTest {

    @Mock
    private SecurityErrorResponseWriter errorResponseWriter;

    @Mock
    private FilterChain filterChain;

    private OAuthRateLimiterProperties properties;
    private OAuthRateLimitingFilter filter;

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
        properties = new OAuthRateLimiterProperties();
        properties.setEnabled(true);
        properties.setIpMaxAttempts(3);
        properties.setIpWindowSeconds(60);
        properties.setMaxTrackedKeys(1000);
        properties.setTrustProxyHeaders(false);

        clock = new MutableClock(Instant.parse("2026-10-26T12:00:00Z"));
        filter = new OAuthRateLimitingFilter(properties, errorResponseWriter, clock);
        filter.init();
    }

    @Test
    @DisplayName("Should pass non-OAuth requests through immediately")
    void nonOAuthRequestsPassThrough() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tickets");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(errorResponseWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should allow requests within limit for /api/auth/oauth2/google")
    void allowsRequestsUnderLimitForGoogleOAuth() throws ServletException, IOException {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/oauth2/google");
            request.setRemoteAddr("192.168.1.100");
            request.setContent("{}".getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);
        }

        verify(filterChain, times(3)).doFilter(any(), any());
        verify(errorResponseWriter, never()).write(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should reject 4th request from same IP exceeding limit on /api/auth/oauth2/google/link")
    void blocksExceededRequestsOnLinkEndpoint() throws ServletException, IOException {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/oauth2/google/link");
            request.setRemoteAddr("10.0.0.5");
            request.setContent("{}".getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);
        }

        MockHttpServletRequest blockedRequest = new MockHttpServletRequest("POST", "/api/auth/oauth2/google/link");
        blockedRequest.setRemoteAddr("10.0.0.5");
        blockedRequest.setContent("{}".getBytes());
        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();

        filter.doFilter(blockedRequest, blockedResponse, filterChain);

        // Chain should not be called a 4th time
        verify(filterChain, times(3)).doFilter(any(), any());
        verify(errorResponseWriter).write(
                eq(blockedRequest),
                eq(blockedResponse),
                eq(HttpStatus.TOO_MANY_REQUESTS),
                contains("Too many authentication attempts")
        );
        assertNotNull(blockedResponse.getHeader(HttpHeaders.RETRY_AFTER));
    }

    @Test
    @DisplayName("Should reset quota after window expires")
    void resetsQuotaAfterWindowExpires() throws ServletException, IOException {
        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/oauth2/google");
            request.setRemoteAddr("10.0.0.99");
            request.setContent("{}".getBytes());
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, filterChain);
        }

        // Advance past 60-second window
        clock.advanceSeconds(61);

        MockHttpServletRequest requestAfterWindow = new MockHttpServletRequest("POST", "/api/auth/oauth2/google");
        requestAfterWindow.setRemoteAddr("10.0.0.99");
        requestAfterWindow.setContent("{}".getBytes());
        MockHttpServletResponse responseAfterWindow = new MockHttpServletResponse();

        filter.doFilter(requestAfterWindow, responseAfterWindow, filterChain);

        verify(filterChain, times(4)).doFilter(any(), any());
        verify(errorResponseWriter, never()).write(any(), any(), any(), any());
    }
}
