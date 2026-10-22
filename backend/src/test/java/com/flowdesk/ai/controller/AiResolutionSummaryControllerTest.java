package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiResolutionSummaryResponse;
import com.flowdesk.ai.service.AiResolutionSummaryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.OffsetDateTime;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiResolutionSummaryControllerTest {

    @Mock
    private AiResolutionSummaryService
            aiResolutionSummaryService;

    private AiResolutionSummaryController
            controller;

    private UUID userId;

    private Jwt jwt;

    @BeforeEach
    void setUp() {

        controller =
                new AiResolutionSummaryController(
                        aiResolutionSummaryService
                );

        userId =
                UUID.randomUUID();

        jwt =
                mock(
                        Jwt.class
                );

        when(
                jwt.getSubject()
        ).thenReturn(
                userId.toString()
        );
    }

    @Test
    void generateDraftDelegatesAuthenticatedUserAndTicket() {

        AiResolutionSummaryResponse expectedResponse =
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
                expectedResponse
        );

        ResponseEntity<AiResolutionSummaryResponse>
                response =
                controller.generateDraft(
                        jwt,
                        "FD-000071"
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertSame(
                expectedResponse,
                response.getBody()
        );

        verify(
                aiResolutionSummaryService
        ).generateDraft(
                userId,
                "FD-000071"
        );
    }
}