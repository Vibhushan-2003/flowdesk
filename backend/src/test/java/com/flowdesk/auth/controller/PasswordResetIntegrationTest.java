package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.ForgotPasswordRequest;
import com.flowdesk.auth.dto.ForgotPasswordResponse;
import com.flowdesk.auth.dto.ResetPasswordRequest;
import com.flowdesk.auth.dto.ResetPasswordResponse;
import com.flowdesk.auth.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PasswordResetService passwordResetService;

    @Test
    @DisplayName("Public POST /api/auth/forgot-password with valid email returns 200 generic message")
    void forgotPasswordValidEmailReturns200() throws Exception {
        when(passwordResetService.requestPasswordReset(any(ForgotPasswordRequest.class)))
                .thenReturn(ForgotPasswordResponse.genericSuccess());

        String body = """
                {
                    "email": "employee@flowdesk.test"
                }
                """;

        mockMvc.perform(
                post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        ForgotPasswordResponse.genericSuccess().message()
                ));

        verify(passwordResetService, times(1)).requestPasswordReset(any(ForgotPasswordRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password with invalid email returns 400 Bad Request")
    void forgotPasswordInvalidEmailReturns400() throws Exception {
        String body = """
                {
                    "email": "not-an-email"
                }
                """;

        mockMvc.perform(
                post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).requestPasswordReset(any());
    }

    @Test
    @DisplayName("Public POST /api/auth/reset-password with valid request returns 200")
    void resetPasswordValidRequestReturns200() throws Exception {
        when(passwordResetService.resetPassword(any(ResetPasswordRequest.class)))
                .thenReturn(ResetPasswordResponse.defaultSuccess());

        String body = """
                {
                    "token": "valid_reset_token_1234567890",
                    "newPassword": "ValidNewPassword123!",
                    "confirmPassword": "ValidNewPassword123!"
                }
                """;

        mockMvc.perform(
                post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Password has been reset successfully. Please sign in with your new password."
                ));

        verify(passwordResetService, times(1)).resetPassword(any(ResetPasswordRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/reset-password with short password returns 400 Bad Request")
    void resetPasswordShortPasswordReturns400() throws Exception {
        String body = """
                {
                    "token": "valid_reset_token_1234567890",
                    "newPassword": "short",
                    "confirmPassword": "short"
                }
                """;

        mockMvc.perform(
                post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest());

        verify(passwordResetService, never()).resetPassword(any());
    }

    @Test
    @DisplayName("Exceeding forgot-password rate limit returns 429 and does not block login attempts")
    void exceedingForgotPasswordLimitDoesNotBlockLogin() throws Exception {
        when(passwordResetService.requestPasswordReset(any(ForgotPasswordRequest.class)))
                .thenReturn(ForgotPasswordResponse.genericSuccess());

        String clientIp = "198.51.100.42";
        String email = "target@flowdesk.test";
        String forgotBody = """
                {
                    "email": "%s"
                }
                """.formatted(email);

        // Default ipEmail limit is 3 attempts per window
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/forgot-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(forgotBody)
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            }))
                    .andExpect(status().isOk());
        }

        // 4th attempt exceeds limit -> 429 Too Many Requests with Retry-After header
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(forgotBody)
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(org.springframework.http.HttpHeaders.RETRY_AFTER));

        // Verify login endpoint quota is completely unaffected by forgot-password attempts
        String loginBody = """
                {
                    "email": "%s",
                    "password": "ValidPassword123!"
                }
                """.formatted(email);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody)
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        }))
                .andExpect(result -> {
                    org.junit.jupiter.api.Assertions.assertNotEquals(429, result.getResponse().getStatus());
                });
    }
}
