package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiReplyDraftMessageRole;

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
class AiReplyDraftContextServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private TicketCommentRepository
            ticketCommentRepository;

    private AiReplyDraftContextService
            contextService;

    @BeforeEach
    void setUp() {

        contextService =
                new AiReplyDraftContextService(
                        ticketAssignmentRepository,
                        ticketCommentRepository
                );
    }

    @Test
    void prepareGenerationBuildsPrivacySafeConversation() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        UUID requesterId =
                UUID.randomUUID();

        UUID supportId =
                UUID.randomUUID();

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User requester =
                mock(User.class);

        User supportEngineer =
                mock(User.class);

        TicketComment requesterComment =
                mock(TicketComment.class);

        TicketComment supportComment =
                mock(TicketComment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000040",
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
                "FD-000040"
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
                "VPN unavailable"
        );

        when(
                ticket.getDescription()
        ).thenReturn(
                "Users cannot connect to VPN."
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
                "I still cannot connect."
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
                "Please restart the VPN client."
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

        AiReplyDraftGenerationContext context =
                contextService
                        .prepareGeneration(
                                engineerId,
                                "  fd-000040  "
                        );

        assertEquals(
                ticketId,
                context.ticketId()
        );

        assertEquals(
                "FD-000040",
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
                "VPN unavailable",
                context.title()
        );

        assertEquals(
                "Users cannot connect to VPN.",
                context.description()
        );

        assertEquals(
                2,
                context
                        .conversation()
                        .size()
        );

        assertEquals(
                AiReplyDraftMessageRole.REQUESTER,
                context
                        .conversation()
                        .get(0)
                        .role()
        );

        assertEquals(
                "I still cannot connect.",
                context
                        .conversation()
                        .get(0)
                        .body()
        );

        assertEquals(
                AiReplyDraftMessageRole.SUPPORT,
                context
                        .conversation()
                        .get(1)
                        .role()
        );

        assertEquals(
                "Please restart the VPN client.",
                context
                        .conversation()
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
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000041",
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
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.ASSIGNED
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
                "FD-000041"
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

        AiReplyDraftGenerationContext context =
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000041"
                        );

        assertEquals(
                0,
                context
                        .conversation()
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
                                "FD-000042",
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
                                                "FD-000042"
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
    void prepareGenerationRejectsFinishedTicket() {

        UUID engineerId =
                UUID.randomUUID();

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000043",
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
                                                "FD-000043"
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
    void prepareGenerationUsesRequesterIdentityOnlyForRoleMapping() {

        UUID engineerId =
                UUID.randomUUID();

        UUID requesterId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        User requester =
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        TicketComment requesterComment =
                mock(TicketComment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000044",
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
                ticket.getStatus()
        ).thenReturn(
                TicketStatus.WAITING_FOR_USER
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
                "Here is the information you requested."
        );

        when(
                ticketCommentRepository
                        .findByTicket_IdOrderByCreatedAtAsc(
                                ticketId
                        )
        ).thenReturn(
                List.of(
                        requesterComment
                )
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000044"
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
                "Login issue"
        );

        when(
                ticket.getDescription()
        ).thenReturn(
                "Requester cannot sign in."
        );

        AiReplyDraftGenerationContext context =
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000044"
                        );

        assertEquals(
                AiReplyDraftMessageRole.REQUESTER,
                context
                        .conversation()
                        .getFirst()
                        .role()
        );

        assertEquals(
                "Here is the information you requested.",
                context
                        .conversation()
                        .getFirst()
                        .body()
        );

        /*
         * The generated AI context intentionally
         * contains no requester id, email or name.
         *
         * Identity is used only inside this service
         * to classify the comment role.
         */
    }

    @Test
    void prepareGenerationNormalizesTicketNumber() {

        UUID engineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000045",
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
                                        "  fd-000045 "
                                )
        );

        verify(
                ticketAssignmentRepository
        ).findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                "FD-000045",
                engineerId
        );
    }
}