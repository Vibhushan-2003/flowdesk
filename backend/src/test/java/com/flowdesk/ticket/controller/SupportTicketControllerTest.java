package com.flowdesk.ticket.controller;

import com.flowdesk.ticket.dto.ResolveTicketRequest;
import com.flowdesk.ticket.dto.SupportTicketResponse;

import com.flowdesk.ticket.service.SupportTicketService;
import com.flowdesk.ticket.service.TicketCommentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketControllerTest {

    @Mock
    private SupportTicketService
            supportTicketService;

    @Mock
    private TicketCommentService
            ticketCommentService;

    private SupportTicketController
            controller;

    private UUID userId;

    private Jwt jwt;

    @BeforeEach
    void setUp() {

        controller =
                new SupportTicketController(
                        supportTicketService,
                        ticketCommentService
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
    void getMyTicketHistoryDelegatesAuthenticatedUser() {

        com.flowdesk.common.dto.PageResponse<com.flowdesk.ticket.dto.SupportTicketSummaryResponse> expectedPage =
                new com.flowdesk.common.dto.PageResponse<>(
                        java.util.List.of(),
                        0,
                        10,
                        0,
                        0,
                        true,
                        true
                );

        when(
                supportTicketService.getMyTicketHistory(
                        userId,
                        0,
                        10,
                        "search",
                        com.flowdesk.ticket.domain.TicketStatus.RESOLVED,
                        com.flowdesk.ticket.domain.TicketPriority.HIGH,
                        "newest"
                )
        ).thenReturn(expectedPage);

        ResponseEntity<com.flowdesk.common.dto.PageResponse<com.flowdesk.ticket.dto.SupportTicketSummaryResponse>> response =
                controller.getMyTicketHistory(
                        jwt,
                        0,
                        10,
                        "search",
                        com.flowdesk.ticket.domain.TicketStatus.RESOLVED,
                        com.flowdesk.ticket.domain.TicketPriority.HIGH,
                        "newest"
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertSame(
                expectedPage,
                response.getBody()
        );

        verify(
                supportTicketService
        ).getMyTicketHistory(
                userId,
                0,
                10,
                "search",
                com.flowdesk.ticket.domain.TicketStatus.RESOLVED,
                com.flowdesk.ticket.domain.TicketPriority.HIGH,
                "newest"
        );
    }

    @Test
    void resolveTicketDelegatesAuthenticatedUserTicketAndRequest() {

        ResolveTicketRequest request =
                new ResolveTicketRequest(
                        "Restarted the service and confirmed normal operation."
                );

        SupportTicketResponse expectedResponse =
                mock(
                        SupportTicketResponse.class
                );

        when(
                supportTicketService
                        .resolveTicket(
                                userId,
                                "FD-000070",
                                request
                        )
        ).thenReturn(
                expectedResponse
        );

        ResponseEntity<SupportTicketResponse> response =
                controller.resolveTicket(
                        jwt,
                        "FD-000070",
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
                supportTicketService
        ).resolveTicket(
                userId,
                "FD-000070",
                request
        );
    }
}