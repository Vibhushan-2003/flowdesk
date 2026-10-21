package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiReplyDraftResponse;
import com.flowdesk.ai.service.AiReplyDraftService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiReplyDraftEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiReplyDraftService
            aiReplyDraftService;

    @Test
    void unauthenticatedUserCannotGenerateReplyDraft()
            throws Exception {

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000060/ai-reply-draft"
                )
        ).andExpect(
                status().isUnauthorized()
        );
    }

    @Test
    void employeeCannotGenerateReplyDraft()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000060/ai-reply-draft"
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
    void supportEngineerCanGenerateReplyDraft()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        AiReplyDraftResponse response =
                new AiReplyDraftResponse(
                        "FD-000060",

                        "Thanks for the update. We are continuing to investigate the issue.",

                        "GOOGLE_GEMINI",

                        "gemini-3.1-flash-lite",

                        OffsetDateTime.parse(
                                "2026-10-21T03:20:00Z"
                        )
                );

        when(
                aiReplyDraftService
                        .generateDraft(
                                userId,
                                "FD-000060"
                        )
        ).thenReturn(
                response
        );

        mockMvc.perform(
                post(
                        "/api/support/tickets/FD-000060/ai-reply-draft"
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
                                "FD-000060"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.draft"
                        ).value(
                                "Thanks for the update. We are continuing to investigate the issue."
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
                                "2026-10-21T03:20:00Z"
                        )
                );

        verify(
                aiReplyDraftService
        ).generateDraft(
                userId,
                "FD-000060"
        );
    }
}