package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.AcceptInvitationRequest;
import com.flowdesk.auth.dto.AcceptInvitationResponse;
import com.flowdesk.user.service.UserInvitationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AcceptInvitationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserInvitationService userInvitationService;

    @Test
    @DisplayName("Public POST /api/auth/accept-invitation with valid payload returns 200")
    void acceptInvitationValidPayloadReturns200() throws Exception {
        when(userInvitationService.acceptInvitation(any(AcceptInvitationRequest.class)))
                .thenReturn(AcceptInvitationResponse.defaultSuccess());

        String body = """
                {
                    "token": "valid-token-12345",
                    "newPassword": "ValidPassword123!",
                    "confirmPassword": "ValidPassword123!"
                }
                """;

        mockMvc.perform(
                post("/api/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Invitation accepted successfully. You can now log in."));

        verify(userInvitationService, times(1)).acceptInvitation(any(AcceptInvitationRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/accept-invitation with missing token returns 400")
    void acceptInvitationMissingTokenReturns400() throws Exception {
        String body = """
                {
                    "token": "",
                    "newPassword": "ValidPassword123!",
                    "confirmPassword": "ValidPassword123!"
                }
                """;

        mockMvc.perform(
                post("/api/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest());

        verify(userInvitationService, never()).acceptInvitation(any());
    }

    @Test
    @DisplayName("POST /api/auth/accept-invitation with short password returns 400")
    void acceptInvitationShortPasswordReturns400() throws Exception {
        String body = """
                {
                    "token": "valid-token",
                    "newPassword": "short",
                    "confirmPassword": "short"
                }
                """;

        mockMvc.perform(
                post("/api/auth/accept-invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isBadRequest());

        verify(userInvitationService, never()).acceptInvitation(any());
    }

    @Test
    @DisplayName("POST /api/auth/accept-invitation exceeding rate limit returns 429 Too Many Requests")
    void acceptInvitationRateLimitingReturns429() throws Exception {
        when(userInvitationService.acceptInvitation(any(AcceptInvitationRequest.class)))
                .thenReturn(AcceptInvitationResponse.defaultSuccess());

        String body = """
                {
                    "token": "rate-limit-token",
                    "newPassword": "ValidPassword123!",
                    "confirmPassword": "ValidPassword123!"
                }
                """;

        String clientIp = "192.168.100.50";

        // Up to 15 attempts allowed per 60 seconds
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(
                    post("/api/auth/accept-invitation")
                            .with(req -> {
                                req.setRemoteAddr(clientIp);
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
            ).andExpect(status().isOk());
        }

        // 16th attempt should be blocked by AcceptInvitationRateLimitingFilter
        mockMvc.perform(
                post("/api/auth/accept-invitation")
                        .with(req -> {
                            req.setRemoteAddr(clientIp);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        )
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }
}
