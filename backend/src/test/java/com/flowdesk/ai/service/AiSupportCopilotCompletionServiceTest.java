package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiSupportCopilotClientResult;

import com.flowdesk.ai.dto.AiSupportCopilotResponse;

import com.flowdesk.ai.tool.AiSupportCopilotTool;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;

import com.flowdesk.audit.service.AuditService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupportCopilotCompletionServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private AuditService
            auditService;

    private AiSupportCopilotCompletionService
            completionService;

    @BeforeEach
    void setUp() {

        completionService =
                new AiSupportCopilotCompletionService(
                        ticketAssignmentRepository,
                        auditService
                );
    }

    @Test
    void completionRevalidatesAssignmentAuditsMetadataAndReturnsAnswer() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        Ticket ticket =
                mock(Ticket.class);

        User engineer =
                mock(User.class);

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentByTicketIdForUpdate(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                engineer
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000080"
        );

        AiSupportCopilotClientResult result =
                new AiSupportCopilotClientResult(
                        "The resolution SLA is the most urgent concern.",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite",
                        List.of(
                                AiSupportCopilotTool
                                        .GET_TICKET_OVERVIEW,
                                AiSupportCopilotTool
                                        .GET_SLA_SNAPSHOT
                        )
                );

        AiSupportCopilotResponse response =
                completionService
                        .completeRun(
                                engineerId,
                                ticketId,
                                result,
                                "support-copilot-v1"
                        );

        assertEquals(
                "FD-000080",
                response.ticketNumber()
        );

        assertEquals(
                "The resolution SLA is the most urgent concern.",
                response.answer()
        );

        assertEquals(
                result.toolsUsed(),
                response.toolsUsed()
        );

        assertEquals(
                "GOOGLE_GEMINI",
                response.provider()
        );

        assertEquals(
                "gemini-3.1-flash-lite",
                response.model()
        );

        assertFalse(
                response.toolsUsed().isEmpty()
        );

        verify(
                auditService
        ).recordUserAction(
                engineer,
                AuditAction.AI_SUPPORT_COPILOT_USED,
                AuditTargetType.TICKET,
                ticketId,
                "FD-000080",
                Map.of(
                        "provider",
                        "GOOGLE_GEMINI",

                        "model",
                        "gemini-3.1-flash-lite",

                        "promptVersion",
                        "support-copilot-v1",

                        "toolsUsed",
                        List.of(
                                "GET_TICKET_OVERVIEW",
                                "GET_SLA_SNAPSHOT"
                        )
                )
        );
    }

    @Test
    void completionRejectsReleasedAssignment() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentByTicketIdForUpdate(
                                ticketId
                        )
        ).thenReturn(
                Optional.empty()
        );

        AiSupportCopilotClientResult result =
                new AiSupportCopilotClientResult(
                        "Temporary answer",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite",
                        List.of()
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                completionService
                                        .completeRun(
                                                engineerId,
                                                ticketId,
                                                result,
                                                "support-copilot-v1"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                auditService
        );
    }

    @Test
    void completionRejectsAssignmentTransferredToAnotherEngineer() {

        UUID originalEngineerId =
                UUID.randomUUID();

        UUID newEngineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User newEngineer =
                mock(User.class);

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentByTicketIdForUpdate(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                newEngineer
        );

        when(
                newEngineer.getId()
        ).thenReturn(
                newEngineerId
        );

        AiSupportCopilotClientResult result =
                new AiSupportCopilotClientResult(
                        "Temporary answer",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite",
                        List.of()
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                completionService
                                        .completeRun(
                                                originalEngineerId,
                                                ticketId,
                                                result,
                                                "support-copilot-v1"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "Ticket is no longer actively assigned to you",
                exception.getReason()
        );

        verifyNoInteractions(
                auditService
        );
    }
}