package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiReplyDraftClientResult;

import com.flowdesk.ai.dto.AiReplyDraftResponse;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;

import com.flowdesk.audit.service.AuditService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReplyDraftCompletionServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private AuditService
            auditService;

    private AiReplyDraftCompletionService
            completionService;

    @BeforeEach
    void setUp() {

        completionService =
                new AiReplyDraftCompletionService(
                        ticketAssignmentRepository,
                        auditService
                );
    }

    @Test
    void completionRechecksAssignmentAuditsAndReturnsTemporaryDraft() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        User engineer =
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
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
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000053"
        );

        when(
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.IN_PROGRESS
        );

        AiReplyDraftClientResult result =
                createResult();

        AiReplyDraftResponse response =
                completionService
                        .completeGeneration(
                                engineerId,
                                ticketId,
                                result,
                                "reply-draft-v1"
                        );

        assertEquals(
                "FD-000053",
                response.ticketNumber()
        );

        assertEquals(
                result.draft(),
                response.draft()
        );

        assertEquals(
                "GOOGLE_GEMINI",
                response.provider()
        );

        assertEquals(
                "gemini-3.1-flash-lite",
                response.model()
        );

        assertNotNull(
                response.generatedAt()
        );

        verify(
                auditService
        ).recordUserAction(
                engineer,
                AuditAction.AI_REPLY_DRAFT_GENERATED,
                AuditTargetType.TICKET,
                ticketId,
                "FD-000053",
                Map.of(
                        "provider",
                        "GOOGLE_GEMINI",

                        "model",
                        "gemini-3.1-flash-lite",

                        "promptVersion",
                        "reply-draft-v1"
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
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                completionService
                                        .completeGeneration(
                                                engineerId,
                                                ticketId,
                                                createResult(),
                                                "reply-draft-v1"
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
    void completionRejectsDifferentEngineer() {

        UUID requestedEngineerId =
                UUID.randomUUID();

        UUID actualEngineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        User engineer =
                mock(User.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
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
                actualEngineerId
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                completionService
                                        .completeGeneration(
                                                requestedEngineerId,
                                                ticketId,
                                                createResult(),
                                                "reply-draft-v1"
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
    void completionRejectsTicketThatFinishedDuringAiCall() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        User engineer =
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
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
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.RESOLVED
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                completionService
                                        .completeGeneration(
                                                engineerId,
                                                ticketId,
                                                createResult(),
                                                "reply-draft-v1"
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                auditService
        );
    }

    private AiReplyDraftClientResult createResult() {

        return new AiReplyDraftClientResult(
                "Thanks for the update. We are continuing to investigate the issue.",
                "GOOGLE_GEMINI",
                "gemini-3.1-flash-lite"
        );
    }
}