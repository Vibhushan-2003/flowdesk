package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiTriageResponse;
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

    private AiTriageController controller;

    private UUID userId;

    private Jwt jwt;

    @BeforeEach
    void setUp() {

        controller =
                new AiTriageController(
                        aiTriageService
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