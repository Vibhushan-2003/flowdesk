package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiResolutionSummaryResponse;
import com.flowdesk.ai.service.AiResolutionSummaryService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.HttpStatus;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiResolutionSummaryEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiResolutionSummaryService
            aiResolutionSummaryService;

    @Test
    void unauthenticatedUserCannotGenerateResolutionSummary()
            throws Exception {

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000071/ai-resolution-summary"
                )
        ).andExpect(
                status().isUnauthorized()
        );

        verifyNoInteractions(
                aiResolutionSummaryService
        );
    }

    @Test
    void employeeCannotGenerateResolutionSummary()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000071/ai-resolution-summary"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_EMPLOYEE"
                                                )
                                        )
                        )
        ).andExpect(
                status().isForbidden()
        );

        verifyNoInteractions(
                aiResolutionSummaryService
        );
    }

    @Test
    void supportEngineerCanGenerateResolutionSummary()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        AiResolutionSummaryResponse response =
                new AiResolutionSummaryResponse(
                        "FD-000071",

                        "The payroll access permissions were corrected and the requester confirmed successful access.",

                        "GOOGLE_GEMINI",

                        "gemini-3.1-flash-lite",

                        OffsetDateTime.parse(
                                "2026-10-22T03:45:00Z"
                        )
                );

        when(
                aiResolutionSummaryService
                        .generateDraft(
                                userId,
                                "FD-000071"
                        )
        ).thenReturn(
                response
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000071/ai-resolution-summary"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_SUPPORT_ENGINEER"
                                                )
                                        )
                        )
        )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.ticketNumber"
                        ).value(
                                "FD-000071"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.draft"
                        ).value(
                                "The payroll access permissions were corrected and the requester confirmed successful access."
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.provider"
                        ).value(
                                "GOOGLE_GEMINI"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.model"
                        ).value(
                                "gemini-3.1-flash-lite"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.generatedAt"
                        ).value(
                                "2026-10-22T03:45:00Z"
                        )
                );

        verify(
                aiResolutionSummaryService
        ).generateDraft(
                userId,
                "FD-000071"
        );
    }

    @Test
    void differentOrUnassignedEngineerReceivesNotFound()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        when(
                aiResolutionSummaryService
                        .generateDraft(
                                userId,
                                "FD-000071"
                        )
        ).thenThrow(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found or it is no longer assigned to you"
                )
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000071/ai-resolution-summary"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_SUPPORT_ENGINEER"
                                                )
                                        )
                        )
        ).andExpect(
                status().isNotFound()
        );
    }

    @Test
    void invalidTicketStateReturnsConflict()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        when(
                aiResolutionSummaryService
                        .generateDraft(
                                userId,
                                "FD-000071"
                        )
        ).thenThrow(
                new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "AI resolution summary drafts can only be generated while ticket is in progress"
                )
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000071/ai-resolution-summary"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_SUPPORT_ENGINEER"
                                                )
                                        )
                        )
        ).andExpect(
                status().isConflict()
        );
    }

    @Test
    void providerFailureReturnsServiceUnavailable()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        when(
                aiResolutionSummaryService
                        .generateDraft(
                                userId,
                                "FD-000071"
                        )
        ).thenThrow(
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI resolution summary drafting is temporarily unavailable"
                )
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000071/ai-resolution-summary"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_SUPPORT_ENGINEER"
                                                )
                                        )
                        )
        ).andExpect(
                status().isServiceUnavailable()
        );
    }
}