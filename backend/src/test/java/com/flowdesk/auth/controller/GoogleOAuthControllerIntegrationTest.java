package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.GoogleAuthRequest;
import com.flowdesk.auth.dto.GoogleOAuthConfigResponse;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.exception.OAuthLinkingRequiredException;
import com.flowdesk.auth.service.GoogleAuthService;
import com.flowdesk.user.domain.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GoogleOAuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleAuthService googleAuthService;

    @Test
    @DisplayName("GET /api/auth/oauth2/config is public and returns config")
    void getOAuthConfig_isPublic() throws Exception {
        when(googleAuthService.getOAuthConfig()).thenReturn(new GoogleOAuthConfigResponse(
                true,
                "test-client-id",
                "http://localhost:5173/auth/google/callback",
                "https://accounts.google.com/o/oauth2/v2/auth"
        ));

        mockMvc.perform(get("/api/auth/oauth2/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.clientId").value("test-client-id"))
                .andExpect(jsonPath("$.redirectUri").value("http://localhost:5173/auth/google/callback"));
    }

    @Test
    @DisplayName("POST /api/auth/oauth2/google returns 409 Conflict with structured fields when linking is required")
    void googleLogin_linkingRequired_returns409WithStructuredFields() throws Exception {
        when(googleAuthService.authenticate(any(GoogleAuthRequest.class)))
                .thenThrow(new OAuthLinkingRequiredException("secret-link-token-123", "alice@example.com", "GOOGLE"));

        String body = """
                {
                    "code": "auth-code-123",
                    "state": "state-abc"
                }
                """;

        mockMvc.perform(
                post("/api/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.linkToken").value("secret-link-token-123"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.provider").value("GOOGLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Please enter your FlowDesk password")));
    }

    @Test
    @DisplayName("POST /api/auth/oauth2/google/link confirms password and completes linking")
    void completeGoogleLink_validRequest_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(googleAuthService.completeLinking("valid-link-token", "ValidPassword123!"))
                .thenReturn(new LoginResponse(
                        "jwt-access-token",
                        "Bearer",
                        userId,
                        "alice@example.com",
                        Set.of(RoleCode.EMPLOYEE)
                ));

        String body = """
                {
                    "linkToken": "valid-link-token",
                    "flowDeskPassword": "ValidPassword123!"
                }
                """;

        mockMvc.perform(
                post("/api/auth/oauth2/google/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-access-token"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));

        verify(googleAuthService).completeLinking("valid-link-token", "ValidPassword123!");
    }

    @Test
    @DisplayName("POST /api/auth/oauth2/google/link validates required password length")
    void completeGoogleLink_shortPassword_returns400() throws Exception {
        String body = """
                {
                    "linkToken": "valid-link-token",
                    "flowDeskPassword": "short"
                }
                """;

        mockMvc.perform(
                post("/api/auth/oauth2/google/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.flowDeskPassword").value("Password must be between 8 and 72 characters"));
    }

    @Test
    @DisplayName("POST /api/auth/oauth2/google returns 403 when user is uninvited")
    void googleLogin_uninvitedUser_returns403() throws Exception {
        when(googleAuthService.authenticate(any(GoogleAuthRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Your Google account is not registered with FlowDesk. Please contact an administrator to receive an invitation."));

        String body = """
                {
                    "code": "auth-code-123",
                    "state": "state-abc"
                }
                """;

        mockMvc.perform(
                post("/api/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isForbidden());
    }
}
