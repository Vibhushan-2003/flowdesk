package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiSupportCopilotRequest;
import com.flowdesk.ai.dto.AiSupportCopilotResponse;

import com.flowdesk.ai.service.AiSupportCopilotService;

import com.flowdesk.ai.tool.AiSupportCopilotTool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.oauth2.jwt.Jwt;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupportCopilotControllerTest {

    @Mock
    private AiSupportCopilotService
            aiSupportCopilotService;

    private AiSupportCopilotController
            controller;

    private UUID userId;

    private Jwt jwt;

    @BeforeEach
    void setUp() {

        controller =
                new AiSupportCopilotController(
                        aiSupportCopilotService
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
    void askDelegatesAuthenticatedUserTicketAndQuestion() {

        AiSupportCopilotRequest request =
                new AiSupportCopilotRequest(
                        "What should I focus on next?"
                );

        AiSupportCopilotResponse expectedResponse =
                new AiSupportCopilotResponse(
                        "FD-000100",

                        "Review the resolution SLA and the latest requester update.",

                        List.of(
                                AiSupportCopilotTool
                                        .GET_PUBLIC_CONVERSATION,

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
                                userId,
                                "FD-000100",
                                request
                        )
        ).thenReturn(
                expectedResponse
        );

        ResponseEntity<AiSupportCopilotResponse>
                response =
                controller.ask(
                        jwt,
                        "FD-000100",
                        request
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
                aiSupportCopilotService
        ).ask(
                userId,
                "FD-000100",
                request
        );
    }
}