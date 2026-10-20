package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiTriageApplicationResponse;
import com.flowdesk.ai.dto.AiTriageResponse;

import com.flowdesk.ai.service.AiTriageApplicationService;
import com.flowdesk.ai.service.AiTriageService;

import com.flowdesk.ticket.domain.TicketPriority;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;

import java.time.OffsetDateTime;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTriageControllerTest {

    @Mock
    private AiTriageService
            aiTriageService;

    @Mock
    private AiTriageApplicationService
            aiTriageApplicationService;

    private AiTriageController controller;

    private UUID userId;

    private Jwt jwt;

    @BeforeEach
    void setUp() {

        controller =
                new AiTriageController(
                        aiTriageService,
                        aiTriageApplicationService
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
    void generateRecommendationDelegatesAuthenticatedUserAndTicket() {

        AiTriageResponse aiResponse =
                createResponse();

        when(
                aiTriageService
                        .generateRecommendation(
                                userId,
                                "FD-000024"
                        )
        ).thenReturn(
                aiResponse
        );

        ResponseEntity<AiTriageResponse>
                response =
                controller
                        .generateRecommendation(
                                jwt,
                                "FD-000024"
                        );

        assertEquals(
                HttpStatus.CREATED,
                response.getStatusCode()
        );

        assertSame(
                aiResponse,
                response.getBody()
        );

        verify(
                aiTriageService
        ).generateRecommendation(
                userId,
                "FD-000024"
        );
    }

    @Test
    void getRecommendationDelegatesAuthenticatedUserAndTicket() {

        AiTriageResponse aiResponse =
                createResponse();

        when(
                aiTriageService
                        .getRecommendation(
                                userId,
                                "FD-000024"
                        )
        ).thenReturn(
                aiResponse
        );

        ResponseEntity<AiTriageResponse>
                response =
                controller
                        .getRecommendation(
                                jwt,
                                "FD-000024"
                        );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertSame(
                aiResponse,
                response.getBody()
        );

        verify(
                aiTriageService
        ).getRecommendation(
                userId,
                "FD-000024"
        );
    }

    @Test
    void applyRecommendationDelegatesAuthenticatedUserAndTicket() {

        OffsetDateTime previousResponseDueAt =
                OffsetDateTime.parse(
                        "2026-10-13T05:00:00Z"
                );

        OffsetDateTime newResponseDueAt =
                OffsetDateTime.parse(
                        "2026-10-13T04:30:00Z"
                );

        OffsetDateTime previousResolutionDueAt =
                OffsetDateTime.parse(
                        "2026-10-14T04:00:00Z"
                );

        OffsetDateTime newResolutionDueAt =
                OffsetDateTime.parse(
                        "2026-10-13T12:00:00Z"
                );

        AiTriageApplicationResponse
                applicationResponse =
                new AiTriageApplicationResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "FD-000024",
                        TicketPriority.MEDIUM,
                        TicketPriority.HIGH,
                        previousResponseDueAt,
                        newResponseDueAt,
                        previousResolutionDueAt,
                        newResolutionDueAt,
                        userId,
                        OffsetDateTime.parse(
                                "2026-10-13T08:45:00Z"
                        )
                );

        when(
                aiTriageApplicationService
                        .applyRecommendation(
                                userId,
                                "FD-000024"
                        )
        ).thenReturn(
                applicationResponse
        );

        ResponseEntity<AiTriageApplicationResponse>
                response =
                controller
                        .applyRecommendation(
                                jwt,
                                "FD-000024"
                        );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertSame(
                applicationResponse,
                response.getBody()
        );

        verify(
                aiTriageApplicationService
        ).applyRecommendation(
                userId,
                "FD-000024"
        );
    }

    private AiTriageResponse createResponse() {

        UUID recommendationId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        return new AiTriageResponse(
                recommendationId,
                ticketId,
                "FD-000024",
                TicketPriority.HIGH,
                new BigDecimal(
                        "0.9500"
                ),
                "Synthetic AI triage reason",
                "GOOGLE_GEMINI",
                "gemini-3.1-flash-lite",
                "triage-v1",
                userId,
                OffsetDateTime.parse(
                        "2026-10-13T05:01:00Z"
                )
        );
    }
}