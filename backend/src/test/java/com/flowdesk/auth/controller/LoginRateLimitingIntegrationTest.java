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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitingIntegrationTest {

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
    @DisplayName("Should allow valid login and return 200 with LoginResponse")
    void validLoginAllowed() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(
                new LoginResponse("mock.jwt.token", "Bearer", UUID.randomUUID(), "user@flowdesk.test", Set.of())
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@flowdesk.test\",\"password\":\"ValidPass123!\"}")
                        .with(request -> {
                            request.setRemoteAddr("192.168.10.1");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(authService, times(1)).login(any());
    }

    @Test
    @DisplayName("Invalid login returns 401 without exhausting quota immediately")
    void invalidLoginReturns401() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@flowdesk.test\",\"password\":\"WrongPassword!\"}")
                        .with(request -> {
                            request.setRemoteAddr("192.168.10.2");
                            return request;
                        }))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Exceeding IP+email limit (5 attempts) returns 429 and does not invoke AuthService on blocked call")
    void exceedingLimitReturns429() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        String clientIp = "192.168.10.3";
        String body = "{\"email\":\"target@flowdesk.test\",\"password\":\"badpass\"}";

        // First 5 attempts return 401
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(request -> {
                                request.setRemoteAddr(clientIp);
                                return request;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        verify(authService, times(5)).login(any());

        // 6th attempt is throttled with HTTP 429 and Retry-After
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("Too many login attempts. Please try again later."));

        // AuthService must NOT be invoked on the 6th blocked request
        verify(authService, times(5)).login(any());
    }

    @Test
    @DisplayName("Other endpoints are unaffected by rate limiting")
    void otherEndpointsUnaffected() throws Exception {
        // Public health endpoint is unaffected
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Oversized login request body returns 413 Payload Too Large and halts processing")
    void oversizedBodyReturns413() throws Exception {
        byte[] oversizedPayload = new byte[9000];

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedPayload)
                        .with(request -> {
                            request.setRemoteAddr("192.168.10.99");
                            return request;
                        }))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.error").value("Payload Too Large"))
                .andExpect(jsonPath("$.message").value("Request payload exceeds maximum allowed size."));

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("Untrusted X-Forwarded-For spoofing cannot bypass rate limiting")
    void untrustedForwardedHeadersCannotBypassRateLimiting() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(
                new BadCredentialsException("Bad credentials")
        );

        String socketIp = "192.168.10.77";

        // Attempt 5 logins for the same email from the same socket IP, spoofing a DIFFERENT X-Forwarded-For each time
        for (int i = 0; i < 5; i++) {
            final int index = i;
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Forwarded-For", "203.0.113." + index)
                            .header("CF-Connecting-IP", "198.51.100." + index)
                            .content("{\"email\":\"spoofed@flowdesk.test\",\"password\":\"badpass\"}")
                            .with(request -> {
                                request.setRemoteAddr(socketIp);
                                return request;
                            }))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt with yet another spoofed IP must still be BLOCKED because rate limiter tracks socket IP
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.99")
                        .content("{\"email\":\"spoofed@flowdesk.test\",\"password\":\"badpass\"}")
                        .with(request -> {
                            request.setRemoteAddr(socketIp);
                            return request;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }
}

