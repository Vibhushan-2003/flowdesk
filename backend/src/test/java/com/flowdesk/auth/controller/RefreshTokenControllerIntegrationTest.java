package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.security.AuthenticatedUser;
import com.flowdesk.auth.service.AuthService;
import com.flowdesk.auth.service.RefreshSessionService;
import com.flowdesk.user.domain.RoleCode;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RefreshSessionService refreshSessionService;

    @Test
    @DisplayName("POST /api/auth/login sets HttpOnly refresh cookie")
    void loginSetsHttpOnlyCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginResponse loginResponse = new LoginResponse(
                "mock-access-token",
                "Bearer",
                userId,
                "user@flowdesk.test",
                Set.of(RoleCode.EMPLOYEE)
        );

        when(authService.loginWithSession(any(), any(), any()))
                .thenReturn(new AuthService.LoginSessionResult(loginResponse, "raw-refresh-token-xyz"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "user@flowdesk.test",
                                    "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("flowdesk_refresh_token=raw-refresh-token-xyz")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")));
    }

    @Test
    @DisplayName("POST /api/auth/refresh rotates token and sets new HttpOnly cookie")
    void refreshRotatesTokenAndSetsCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                userId,
                "user@flowdesk.test",
                "hash",
                Set.of(RoleCode.EMPLOYEE),
                true,
                1L
        );

        when(refreshSessionService.rotateSession(eq("existing-refresh-token"), any(), any()))
                .thenReturn(new RefreshSessionService.TokenRefreshResult(
                        "new-refresh-token-abc",
                        "new-access-token-123",
                        user
                ));

        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("flowdesk_refresh_token", "existing-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token-123"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("flowdesk_refresh_token=new-refresh-token-abc")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")));
    }

    @Test
    @DisplayName("POST /api/auth/refresh rejects missing cookie with 401 Unauthorized")
    void refreshRejectsMissingCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout revokes session and clears cookie")
    void logoutClearsCookieAndRevokes() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("flowdesk_refresh_token", "token-to-revoke")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(refreshSessionService).revokeSession("token-to-revoke");
    }

    @Test
    @DisplayName("CORS preflight allows credentials and required headers")
    void corsAllowsCredentialsAndHeaders() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested-With,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh rejects untrusted origin with 403 Forbidden")
    void refreshRejectsUntrustedOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://evil-attacker.com")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("flowdesk_refresh_token", "existing-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/auth/logout rejects untrusted origin with 403 Forbidden")
    void logoutRejectsUntrustedOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.ORIGIN, "http://evil-attacker.com")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .cookie(new Cookie("flowdesk_refresh_token", "existing-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/auth/refresh rejects browser request missing anti-CSRF custom header")
    void refreshRejectsMissingCustomHeaderWhenOriginPresent() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .cookie(new Cookie("flowdesk_refresh_token", "existing-token")))
                .andExpect(status().isForbidden());
    }
}

