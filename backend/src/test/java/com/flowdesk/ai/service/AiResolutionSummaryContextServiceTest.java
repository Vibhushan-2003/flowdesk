package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiResolutionSummaryMessageRole;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketComment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiResolutionSummaryContextServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private TicketCommentRepository
            ticketCommentRepository;

    private AiResolutionSummaryContextService
            contextService;

    @BeforeEach
    void setUp() {

        contextService =
                new AiResolutionSummaryContextService(
                        ticketAssignmentRepository,
                        ticketCommentRepository
                );
    }

    @Test
    void prepareGenerationBuildsPrivacySafeContext() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        UUID requesterId =
                UUID.randomUUID();

        UUID supportId =
                UUID.randomUUID();

        Ticket ticket =
                mock(
                        Ticket.class
                );

        TicketAssignment assignment =
                mock(
                        TicketAssignment.class
                );

        User requester =
                mock(
                        User.class
                );

        User supportEngineer =
                mock(
                        User.class
                );

        TicketComment requesterComment =
                mock(
                        TicketComment.class
                );

        TicketComment supportComment =
                mock(
                        TicketComment.class
                );

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000060",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
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
                "FD-000060"
        );

        when(
                ticket.getType()
        ).thenReturn(
                TicketType.INCIDENT
        );

        when(
                ticket.getPriority()
        ).thenReturn(
                TicketPriority.HIGH
        );

        when(
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.IN_PROGRESS
        );

        when(
                ticket.getTitle()
        ).thenReturn(
                "Payroll access issue"
        );

        when(
                ticket.getDescription()
        ).thenReturn(
                "Requester cannot access payroll."
        );

        when(
                ticket.getCreatedByUser()
        ).thenReturn(
                requester
        );

        when(
                requester.getId()
        ).thenReturn(
                requesterId
        );

        when(
                requesterComment.getAuthorUser()
        ).thenReturn(
                requester
        );

        when(
                requesterComment.getBody()
        ).thenReturn(
                "I still cannot open the payroll page."
        );

        when(
                supportComment.getAuthorUser()
        ).thenReturn(
                supportEngineer
        );

        when(
                supportEngineer.getId()
        ).thenReturn(
                supportId
        );

        when(
                supportComment.getBody()
        ).thenReturn(
                "The payroll access permissions were corrected."
        );

        when(
                ticketCommentRepository
                        .findByTicket_IdOrderByCreatedAtAsc(
                                ticketId
                        )
        ).thenReturn(
                List.of(
                        requesterComment,
                        supportComment
                )
        );

        AiResolutionSummaryGenerationContext context =
                contextService
                        .prepareGeneration(
                                engineerId,
                                "  fd-000060  "
                        );

        assertEquals(
                ticketId,
                context.ticketId()
        );

        assertEquals(
                "FD-000060",
                context.ticketNumber()
        );

        assertEquals(
                TicketType.INCIDENT,
                context.type()
        );

        assertEquals(
                TicketPriority.HIGH,
                context.priority()
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                context.status()
        );

        assertEquals(
                "Payroll access issue",
                context.title()
        );

        assertEquals(
                "Requester cannot access payroll.",
                context.description()
        );

        assertEquals(
                2,
                context.conversation()
                        .size()
        );

        assertEquals(
                AiResolutionSummaryMessageRole.REQUESTER,
                context.conversation()
                        .get(0)
                        .role()
        );

        assertEquals(
                "I still cannot open the payroll page.",
                context.conversation()
                        .get(0)
                        .body()
        );

        assertEquals(
                AiResolutionSummaryMessageRole.SUPPORT,
                context.conversation()
                        .get(1)
                        .role()
        );

        assertEquals(
                "The payroll access permissions were corrected.",
                context.conversation()
                        .get(1)
                        .body()
        );
    }

    @Test
    void prepareGenerationAllowsEmptyConversation() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        User requester =
                mock(
                        User.class
                );

        Ticket ticket =
                mock(
                        Ticket.class
                );

        TicketAssignment assignment =
                mock(
                        TicketAssignment.class
                );

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000061",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.IN_PROGRESS
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getCreatedByUser()
        ).thenReturn(
                requester
        );

        when(
                requester.getId()
        ).thenReturn(
                UUID.randomUUID()
        );

        when(
                ticketCommentRepository
                        .findByTicket_IdOrderByCreatedAtAsc(
                                ticketId
                        )
        ).thenReturn(
                List.of()
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000061"
        );

        when(
                ticket.getType()
        ).thenReturn(
                TicketType.INCIDENT
        );

        when(
                ticket.getPriority()
        ).thenReturn(
                TicketPriority.MEDIUM
        );

        when(
                ticket.getTitle()
        ).thenReturn(
                "Printer unavailable"
        );

        when(
                ticket.getDescription()
        ).thenReturn(
                "Printer is offline."
        );

        AiResolutionSummaryGenerationContext context =
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000061"
                        );

        assertEquals(
                0,
                context.conversation()
                        .size()
        );
    }

    @Test
    void prepareGenerationRejectsTicketWithoutActiveAssignment() {

        UUID engineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000062",
                                engineerId
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                contextService
                                        .prepareGeneration(
                                                engineerId,
                                                "FD-000062"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                ticketCommentRepository
        );
    }

    @Test
    void prepareGenerationRejectsAssignedTicket() {

        UUID engineerId =
                UUID.randomUUID();

        Ticket ticket =
                mock(
                        Ticket.class
                );

        TicketAssignment assignment =
                mock(
                        TicketAssignment.class
                );

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000063",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.ASSIGNED
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                contextService
                                        .prepareGeneration(
                                                engineerId,
                                                "FD-000063"
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                ticketCommentRepository
        );
    }

    @Test
    void prepareGenerationRejectsWaitingForUserTicket() {

        UUID engineerId =
                UUID.randomUUID();

        Ticket ticket =
                mock(
                        Ticket.class
                );

        TicketAssignment assignment =
                mock(
                        TicketAssignment.class
                );

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000064",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.WAITING_FOR_USER
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                contextService
                                        .prepareGeneration(
                                                engineerId,
                                                "FD-000064"
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                ticketCommentRepository
        );
    }

    @Test
    void prepareGenerationRejectsFinishedTicket() {

        UUID engineerId =
                UUID.randomUUID();

        Ticket ticket =
                mock(
                        Ticket.class
                );

        TicketAssignment assignment =
                mock(
                        TicketAssignment.class
                );

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000065",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
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
                                contextService
                                        .prepareGeneration(
                                                engineerId,
                                                "FD-000065"
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                ticketCommentRepository
        );
    }

    @Test
    void prepareGenerationNormalizesTicketNumber() {

        UUID engineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000066",
                                engineerId
                        )
        ).thenReturn(
                Optional.empty()
        );

        assertThrows(
                ResponseStatusException.class,
                () ->
                        contextService
                                .prepareGeneration(
                                        engineerId,
                                        "  fd-000066  "
                                )
        );

        verify(
                ticketAssignmentRepository
        ).findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                "FD-000066",
                engineerId
        );
    }
}