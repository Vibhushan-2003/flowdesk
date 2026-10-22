package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiSupportCopilotRequest;
import com.flowdesk.ai.dto.AiSupportCopilotResponse;

import com.flowdesk.ai.service.AiSupportCopilotService;

import com.flowdesk.ai.tool.AiSupportCopilotTool;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiSupportCopilotEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiSupportCopilotService
            aiSupportCopilotService;

    @Test
    void unauthenticatedUserCannotUseCopilot()
            throws Exception {

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000100/ai-copilot"
                )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "question":
                                    "What should I focus on next?"
                                }
                                """
                        )
        ).andExpect(
                status().isUnauthorized()
        );

        verifyNoInteractions(
                aiSupportCopilotService
        );
    }

    @Test
    void employeeCannotUseCopilot()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000100/ai-copilot"
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
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "question":
                                    "What should I focus on next?"
                                }
                                """
                        )
        ).andExpect(
                status().isForbidden()
        );

        verifyNoInteractions(
                aiSupportCopilotService
        );
    }

    @Test
    void supportEngineerCanUseCopilot()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        AiSupportCopilotResponse response =
                new AiSupportCopilotResponse(
                        "FD-000100",

                        "The resolution SLA is the most urgent concern.",

                        List.of(
                                AiSupportCopilotTool
                                        .GET_TICKET_OVERVIEW,

                                AiSupportCopilotTool
                                        .GET_SLA_SNAPSHOT
                        ),

                        "GOOGLE_GEMINI",

                        "gemini-3.1-flash-lite",

                        OffsetDateTime.parse(
                                "2026-10-22T09:30:00Z"
                        )
                );

        when(
                aiSupportCopilotService
                        .ask(
                                org.mockito.ArgumentMatchers.eq(
                                        userId
                                ),
                                org.mockito.ArgumentMatchers.eq(
                                        "FD-000100"
                                ),
                                any(
                                        AiSupportCopilotRequest.class
                                )
                        )
        ).thenReturn(
                response
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000100/ai-copilot"
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
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "question":
                                    "What should I focus on next?"
                                }
                                """
                        )
        )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.ticketNumber"
                        ).value(
                                "FD-000100"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.answer"
                        ).value(
                                "The resolution SLA is the most urgent concern."
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.toolsUsed[0]"
                        ).value(
                                "GET_TICKET_OVERVIEW"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.toolsUsed[1]"
                        ).value(
                                "GET_SLA_SNAPSHOT"
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
                                "2026-10-22T09:30:00Z"
                        )
                );

        verify(
                aiSupportCopilotService
        ).ask(
                org.mockito.ArgumentMatchers.eq(
                        userId
                ),
                org.mockito.ArgumentMatchers.eq(
                        "FD-000100"
                ),
                any(
                        AiSupportCopilotRequest.class
                )
        );
    }

    @Test
    void blankQuestionIsRejectedBeforeServiceCall()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000100/ai-copilot"
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
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "question": "   "
                                }
                                """
                        )
        ).andExpect(
                status().isBadRequest()
        );

        verifyNoInteractions(
                aiSupportCopilotService
        );
    }

    @Test
    void differentOrUnassignedEngineerReceivesNotFound()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        when(
                aiSupportCopilotService
                        .ask(
                                org.mockito.ArgumentMatchers.eq(
                                        userId
                                ),
                                org.mockito.ArgumentMatchers.eq(
                                        "FD-000100"
                                ),
                                any(
                                        AiSupportCopilotRequest.class
                                )
                        )
        ).thenThrow(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found or it is no longer assigned to you"
                )
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000100/ai-copilot"
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
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "question":
                                    "What should I focus on next?"
                                }
                                """
                        )
        ).andExpect(
                status().isNotFound()
        );
    }

    @Test
    void providerFailureReturnsServiceUnavailable()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        when(
                aiSupportCopilotService
                        .ask(
                                org.mockito.ArgumentMatchers.eq(
                                        userId
                                ),
                                org.mockito.ArgumentMatchers.eq(
                                        "FD-000100"
                                ),
                                any(
                                        AiSupportCopilotRequest.class
                                )
                        )
        ).thenThrow(
                new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "AI Support Copilot is temporarily unavailable"
                )
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000100/ai-copilot"
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
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "question":
                                    "What should I focus on next?"
                                }
                                """
                        )
        ).andExpect(
                status().isServiceUnavailable()
        );
    }
}