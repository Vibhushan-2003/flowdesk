package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.ratelimit.LoginRateLimitingFilter;
import com.flowdesk.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.rate-limit.login.enabled=true",
        "app.security.rate-limit.login.trust-proxy-headers=true",
        "app.security.rate-limit.login.trusted-proxies=172.18.0.2"
})
class ProxyAwareLoginRateLimitingIntegrationTest {

    private static final String TRUSTED_PROXY_IP = "172.18.0.2"; // Docker container bridge IP

    @Autowired
    private MockMvc mockMvc;

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

    @Test
    @DisplayName("1. Two distinct clients behind trusted proxy receive independent rate-limit buckets")
    void twoDistinctClientsBehindProxyHaveIndependentBuckets() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        String clientA = "203.0.113.10";
        String clientB = "203.0.113.20";
        String body = "{\"email\":\"target@flowdesk.test\",\"password\":\"badpass\"}";

        // Client A exhausts their 5 attempts through trusted reverse proxy
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(req -> {
                                req.setRemoteAddr(TRUSTED_PROXY_IP);
                                req.addHeader("X-Forwarded-For", clientA);
                                return req;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        // Client A's 6th attempt is throttled (429)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(req -> {
                            req.setRemoteAddr(TRUSTED_PROXY_IP);
                            req.addHeader("X-Forwarded-For", clientA);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // Client B connecting via the same trusted proxy is NOT throttled (independent quota bucket)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(req -> {
                            req.setRemoteAddr(TRUSTED_PROXY_IP);
                            req.addHeader("X-Forwarded-For", clientB);
                            return req;
                        }))
                .andExpect(status().isUnauthorized()); // Allowed through to authService, returning 401 not 429

        verify(authService, times(6)).login(any());
    }

    @Test
    @DisplayName("2. Repeated requests from one client behind trusted proxy are limited")
    void repeatedRequestsFromClientAreLimited() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        String clientIp = "198.51.100.15";
        String body = "{\"email\":\"victim@flowdesk.test\",\"password\":\"badpass\"}";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(req -> {
                                req.setRemoteAddr(TRUSTED_PROXY_IP);
                                req.addHeader("X-Forwarded-For", clientIp);
                                return req;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(req -> {
                            req.setRemoteAddr(TRUSTED_PROXY_IP);
                            req.addHeader("X-Forwarded-For", clientIp);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"));
    }

    @Test
    @DisplayName("3. Spoofed forwarded headers cannot bypass the limiter")
    void spoofedHeadersCannotBypassLimiter() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        String realAttackerIp = "203.0.113.99";
        String body = "{\"email\":\"target@flowdesk.test\",\"password\":\"wrong\"}";

        // Attacker prepends varying fake IPs to X-Forwarded-For on each request
        for (int i = 1; i <= 5; i++) {
            String spoofedHeader = "10.0.0." + i + ", " + realAttackerIp;
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(req -> {
                                req.setRemoteAddr(TRUSTED_PROXY_IP);
                                req.addHeader("X-Forwarded-For", spoofedHeader);
                                return req;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        // On the 6th attempt with another spoofed prefix, limiter still identifies realAttackerIp and blocks
        String finalSpoof = "8.8.8.8, " + realAttackerIp;
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(req -> {
                            req.setRemoteAddr(TRUSTED_PROXY_IP);
                            req.addHeader("X-Forwarded-For", finalSpoof);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("4. Direct untrusted requests do not gain privileged IP identity")
    void directUntrustedRequestsDoNotGainPrivilegedIdentity() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        String untrustedDirectIp = "198.51.100.77"; // Direct connection bypassing proxy
        String spoofedVictimIp = "203.0.113.55";
        String body = "{\"email\":\"target@flowdesk.test\",\"password\":\"wrong\"}";

        // Attacker directly connects and attempts to claim victim's IP
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(req -> {
                                req.setRemoteAddr(untrustedDirectIp); // Socket is untrusted
                                req.addHeader("X-Forwarded-For", spoofedVictimIp);
                                return req;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        // Attacker IP (198.51.100.77) is throttled on 6th attempt
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(req -> {
                            req.setRemoteAddr(untrustedDirectIp);
                            req.addHeader("X-Forwarded-For", spoofedVictimIp);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests());

        // Meanwhile, real victim IP connecting via trusted proxy is NOT throttled (attacker couldn't consume victim quota)
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(req -> {
                            req.setRemoteAddr(TRUSTED_PROXY_IP);
                            req.addHeader("X-Forwarded-For", spoofedVictimIp);
                            return req;
                        }))
                .andExpect(status().isUnauthorized()); // Allowed through, returning 401
    }

    @Test
    @DisplayName("5. Existing login authentication remains functional behind trusted proxy")
    void validLoginAllowedBehindTrustedProxy() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(
                new LoginResponse("mock.jwt.token", "Bearer", UUID.randomUUID(), "legit@flowdesk.test", Set.of())
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"legit@flowdesk.test\",\"password\":\"Secret123!\"}")
                        .with(req -> {
                            req.setRemoteAddr(TRUSTED_PROXY_IP);
                            req.addHeader("X-Forwarded-For", "203.0.113.77");
                            return req;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService, times(1)).login(any());
    }
}

