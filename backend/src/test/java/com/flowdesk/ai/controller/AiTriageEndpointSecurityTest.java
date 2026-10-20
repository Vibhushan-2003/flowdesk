package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiTriageApplicationResponse;
import com.flowdesk.ai.dto.AiTriageResponse;

import com.flowdesk.ai.service.AiTriageApplicationService;
import com.flowdesk.ai.service.AiTriageService;

import com.flowdesk.ticket.domain.TicketPriority;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import java.time.OffsetDateTime;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiTriageEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiTriageService
            aiTriageService;

    @MockitoBean
    private AiTriageApplicationService
            aiTriageApplicationService;

    @Test
    void unauthenticatedUserCannotGetAiTriage()
            throws Exception {

        mockMvc.perform(
                get(
                        "/api/support/tickets/FD-000024/ai-triage"
                )
        ).andExpect(
                status().isUnauthorized()
        );
    }

    @Test
    void unauthenticatedUserCannotGenerateAiTriage()
            throws Exception {

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000024/ai-triage"
                )
        ).andExpect(
                status().isUnauthorized()
        );
    }

    @Test
    void unauthenticatedUserCannotApplyAiTriage()
            throws Exception {

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000024/ai-triage/apply"
                )
        ).andExpect(
                status().isUnauthorized()
        );
    }

    @Test
    void employeeCannotGetAiTriage()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                get(
                        "/api/support/tickets/FD-000024/ai-triage"
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
    }

    @Test
    void employeeCannotGenerateAiTriage()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000024/ai-triage"
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
    }

    @Test
    void employeeCannotApplyAiTriage()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000024/ai-triage/apply"
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
    }

    @Test
    void supportEngineerCanGetAiTriage()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        AiTriageResponse response =
                createResponse(
                        userId
                );

        when(
                aiTriageService
                        .getRecommendation(
                                userId,
                                "FD-000024"
                        )
        ).thenReturn(
                response
        );

        mockMvc.perform(
                get(
                        "/api/support/tickets/FD-000024/ai-triage"
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
                                "FD-000024"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.suggestedPriority"
                        ).value(
                                "HIGH"
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
                                "$.applied"
                        ).value(
                                false
                        )
                );

        verify(
                aiTriageService
        ).getRecommendation(
                userId,
                "FD-000024"
        );
    }

    @Test
    void supportEngineerCanGenerateAiTriage()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        AiTriageResponse response =
                createResponse(
                        userId
                );

        when(
                aiTriageService
                        .generateRecommendation(
                                userId,
                                "FD-000024"
                        )
        ).thenReturn(
                response
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000024/ai-triage"
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
                        status().isCreated()
                )
                .andExpect(
                        jsonPath(
                                "$.ticketNumber"
                        ).value(
                                "FD-000024"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.suggestedPriority"
                        ).value(
                                "HIGH"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.confidence"
                        ).value(
                                0.95
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.applied"
                        ).value(
                                false
                        )
                );

        verify(
                aiTriageService
        ).generateRecommendation(
                userId,
                "FD-000024"
        );
    }

    @Test
    void supportEngineerCanApplyAiTriage()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        AiTriageApplicationResponse response =
                createApplicationResponse(
                        userId
                );

        when(
                aiTriageApplicationService
                        .applyRecommendation(
                                userId,
                                "FD-000024"
                        )
        ).thenReturn(
                response
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000024/ai-triage/apply"
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
                                "FD-000024"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.previousPriority"
                        ).value(
                                "MEDIUM"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.appliedPriority"
                        ).value(
                                "HIGH"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.appliedByUserId"
                        ).value(
                                userId.toString()
                        )
                );

        verify(
                aiTriageApplicationService
        ).applyRecommendation(
                userId,
                "FD-000024"
        );
    }

    private AiTriageResponse createResponse(
            UUID generatedByUserId
    ) {

        return new AiTriageResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FD-000024",
                TicketPriority.HIGH,
                new BigDecimal(
                        "0.9500"
                ),
                "Synthetic AI triage reason",
                "GOOGLE_GEMINI",
                "gemini-3.1-flash-lite",
                "triage-v1",
                generatedByUserId,
                OffsetDateTime.parse(
                        "2026-10-13T05:01:00Z"
                )
        );
    }

    private AiTriageApplicationResponse
            createApplicationResponse(
                    UUID appliedByUserId
            ) {

        return new AiTriageApplicationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "FD-000024",
                TicketPriority.MEDIUM,
                TicketPriority.HIGH,
                OffsetDateTime.parse(
                        "2026-10-13T05:00:00Z"
                ),
                OffsetDateTime.parse(
                        "2026-10-13T04:30:00Z"
                ),
                OffsetDateTime.parse(
                        "2026-10-14T04:00:00Z"
                ),
                OffsetDateTime.parse(
                        "2026-10-13T12:00:00Z"
                ),
                appliedByUserId,
                OffsetDateTime.parse(
                        "2026-10-13T08:45:00Z"
                )
        );
    }
}