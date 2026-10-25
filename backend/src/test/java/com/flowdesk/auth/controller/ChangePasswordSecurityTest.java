package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.ChangePasswordRequest;
import com.flowdesk.auth.dto.ChangePasswordResponse;
import com.flowdesk.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private static final String VALID_CURRENT_PASSWORD = "OldPassword123!";
    private static final String VALID_NEW_PASSWORD = "NewPassword456!";

    @Test
    @DisplayName("Unauthenticated request to change-password must be rejected with 401")
    void unauthenticatedRequestRejected() throws Exception {
        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(VALID_CURRENT_PASSWORD, VALID_NEW_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ).andExpect(status().isUnauthorized());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Authenticated user with valid credentials can change password")
    void authenticatedUserCanChangePassword() throws Exception {
        UUID userId = UUID.randomUUID();

        when(authService.changePassword(eq(userId), any(ChangePasswordRequest.class)))
                .thenReturn(new ChangePasswordResponse("Password changed successfully"));

        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(VALID_CURRENT_PASSWORD, VALID_NEW_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(jwt().jwt(createJwt(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        verify(authService).changePassword(eq(userId), any(ChangePasswordRequest.class));
    }

    @Test
    @DisplayName("Wrong current password results in 400 Bad Request")
    void wrongCurrentPasswordReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        when(authService.changePassword(eq(userId), any(ChangePasswordRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid current password"));

        String body = """
                {
                    "currentPassword": "WrongCurrentPassword123!",
                    "newPassword": "%s"
                }
                """.formatted(VALID_NEW_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(jwt().jwt(createJwt(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid current password"));
    }

    @Test
    @DisplayName("Same old and new password results in 400 Bad Request")
    void sameOldAndNewPasswordReturnsBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        when(authService.changePassword(eq(userId), any(ChangePasswordRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password cannot be the same as current password"));

        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(VALID_CURRENT_PASSWORD, VALID_CURRENT_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(jwt().jwt(createJwt(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password cannot be the same as current password"));
    }

    @Test
    @DisplayName("Blank new password is rejected by validation with 400 Bad Request")
    void blankNewPasswordRejected() throws Exception {
        UUID userId = UUID.randomUUID();

        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": ""
                }
                """.formatted(VALID_CURRENT_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(jwt().jwt(createJwt(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("New password under 8 characters is rejected by validation with 400 Bad Request")
    void shortNewPasswordRejected() throws Exception {
        UUID userId = UUID.randomUUID();

        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "short"
                }
                """.formatted(VALID_CURRENT_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(jwt().jwt(createJwt(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("Inactive account attempting password change is rejected with 401")
    void inactiveAccountRejected() throws Exception {
        UUID userId = UUID.randomUUID();

        when(authService.changePassword(eq(userId), any(ChangePasswordRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is unavailable"));

        String body = """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(VALID_CURRENT_PASSWORD, VALID_NEW_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(jwt().jwt(createJwt(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Endpoint uses subject from JWT and ignores any attempted user ID in request body")
    void cannotChangeAnotherUsersPasswordViaBody() throws Exception {
        UUID callerUserId = UUID.randomUUID();
        UUID targetedUserId = UUID.randomUUID();

        when(authService.changePassword(eq(callerUserId), any(ChangePasswordRequest.class)))
                .thenReturn(new ChangePasswordResponse("Password changed successfully"));

        // Attacker passes another user's id in the payload
        String spoofBody = """
                {
                    "userId": "%s",
                    "currentPassword": "%s",
                    "newPassword": "%s"
                }
                """.formatted(targetedUserId, VALID_CURRENT_PASSWORD, VALID_NEW_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(jwt().jwt(createJwt(callerUserId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(spoofBody)
        )
                .andExpect(status().isOk());

        // Verify service was called with callerUserId, NOT targetedUserId
        verify(authService).changePassword(eq(callerUserId), any(ChangePasswordRequest.class));
        verify(authService, never()).changePassword(eq(targetedUserId), any());
    }

    @Test
    @DisplayName("Admin role cannot bypass current password requirement")
    void adminCannotBypassCurrentPassword() throws Exception {
        UUID adminId = UUID.randomUUID();

        when(authService.changePassword(eq(adminId), any(ChangePasswordRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid current password"));

        String body = """
                {
                    "currentPassword": "WrongAdminPassword!",
                    "newPassword": "%s"
                }
                """.formatted(VALID_NEW_PASSWORD);

        mockMvc.perform(
                post("/api/auth/change-password")
                        .with(
                                jwt()
                                        .jwt(createJwt(adminId))
                                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid current password"));
    }

    private Jwt createJwt(UUID userId) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .claim("email", "user@example.com")
                .claim("roles", List.of("EMPLOYEE"))
                .claim("credential_version", 1L)
                .build();
    }
}
