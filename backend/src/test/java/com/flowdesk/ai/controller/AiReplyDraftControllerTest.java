package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiReplyDraftResponse;
import com.flowdesk.ai.service.AiReplyDraftService;

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
class AiReplyDraftControllerTest {

    @Mock
    private AiReplyDraftService
            aiReplyDraftService;

    private AiReplyDraftController
            controller;

    private UUID userId;

    private Jwt jwt;

    @BeforeEach
    void setUp() {

        controller =
                new AiReplyDraftController(
                        aiReplyDraftService
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

        AiReplyDraftResponse expectedResponse =
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
                expectedResponse
        );

        ResponseEntity<AiReplyDraftResponse>
                response =
                controller.generateDraft(
                        jwt,
                        "FD-000060"
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
                aiReplyDraftService
        ).generateDraft(
                userId,
                "FD-000060"
        );
    }
}