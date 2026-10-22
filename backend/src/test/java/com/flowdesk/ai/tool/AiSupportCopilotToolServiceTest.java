package com.flowdesk.ai.tool;

import com.flowdesk.sla.domain.SlaStatus;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupportCopilotToolServiceTest {

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private TicketCommentRepository
            ticketCommentRepository;

    private AiSupportCopilotToolService
            toolService;

    @BeforeEach
    void setUp() {

        toolService =
                new AiSupportCopilotToolService(
                        ticketAssignmentRepository,
                        ticketCommentRepository
                );
    }

    @Test
    void ticketOverviewReturnsOnlySafeBusinessContext() {

        UUID engineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        Ticket ticket =
                mock(Ticket.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000050",
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
                "The requester cannot connect to the corporate VPN."
        );

        AiSupportCopilotTicketOverview result =
                toolService
                        .getTicketOverview(
                                engineerId,
                                "  fd-000050  "
                        );

        assertEquals(
                TicketType.INCIDENT,
                result.type()
        );

        assertEquals(
                TicketPriority.HIGH,
                result.priority()
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                result.status()
        );

        assertEquals(
                "VPN unavailable",
                result.title()
        );

        assertEquals(
                "The requester cannot connect to the corporate VPN.",
                result.description()
        );

        /*
         * The AI-facing record deliberately contains
         * no:
         *
         * - ticket UUID
         * - requester identity
         * - engineer identity
         * - assignment id
         * - audit data
         */
        assertEquals(
                5,
                AiSupportCopilotTicketOverview
                        .class
                        .getRecordComponents()
                        .length
        );

        verify(
                ticketAssignmentRepository
        ).findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                "FD-000050",
                engineerId
        );

        verifyNoInteractions(
                ticketCommentRepository
        );
    }

    @Test
    void publicConversationReducesAuthorsToRequesterAndSupportRoles() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        UUID requesterId =
                UUID.randomUUID();

        UUID supportUserId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        Ticket ticket =
                mock(Ticket.class);

        User requester =
                mock(User.class);

        User supportUser =
                mock(User.class);

        TicketComment requesterComment =
                mock(TicketComment.class);

        TicketComment supportComment =
                mock(TicketComment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000051",
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
                "The VPN still does not connect."
        );

        when(
                supportComment.getAuthorUser()
        ).thenReturn(
                supportUser
        );

        when(
                supportUser.getId()
        ).thenReturn(
                supportUserId
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

        List<AiSupportCopilotConversationMessage>
                result =
                toolService
                        .getPublicConversation(
                                engineerId,
                                "FD-000051"
                        );

        assertEquals(
                2,
                result.size()
        );

        assertEquals(
                AiSupportCopilotConversationMessage
                        .Role.REQUESTER,
                result.get(0).role()
        );

        assertEquals(
                "The VPN still does not connect.",
                result.get(0).body()
        );

        assertEquals(
                AiSupportCopilotConversationMessage
                        .Role.SUPPORT,
                result.get(1).role()
        );

        assertEquals(
                "Please restart the VPN client.",
                result.get(1).body()
        );

        /*
         * The result contract exposes only:
         *
         * role + body
         *
         * It cannot expose author UUID, email,
         * first name or last name.
         */
        assertEquals(
                2,
                AiSupportCopilotConversationMessage
                        .class
                        .getRecordComponents()
                        .length
        );
    }

    @Test
    void slaSnapshotReturnsDerivedStatusesAndDeadlineDistance() {

        UUID engineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        Ticket ticket =
                mock(Ticket.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000052",
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
                ticket.evaluateResponseSla(
                        any(
                                OffsetDateTime.class
                        )
                )
        ).thenReturn(
                SlaStatus.PENDING
        );

        when(
                ticket.evaluateResolutionSla(
                        any(
                                OffsetDateTime.class
                        )
                )
        ).thenReturn(
                SlaStatus.BREACHED
        );

        OffsetDateTime now =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        when(
                ticket.getResponseDueAt()
        ).thenReturn(
                now.plusHours(2)
        );

        when(
                ticket.getResolutionDueAt()
        ).thenReturn(
                now.minusMinutes(15)
        );

        AiSupportCopilotSlaSnapshot result =
                toolService
                        .getSlaSnapshot(
                                engineerId,
                                "FD-000052"
                        );

        assertEquals(
                SlaStatus.PENDING,
                result.responseStatus()
        );

        assertEquals(
                SlaStatus.BREACHED,
                result.resolutionStatus()
        );

        assertNotNull(
                result.responseMinutesUntilDue()
        );

        assertNotNull(
                result.resolutionMinutesUntilDue()
        );

        /*
         * Approximately two hours remain.
         *
         * We intentionally use a small range because
         * the service obtains the current UTC time
         * during execution.
         */
        assertTrue(
                result.responseMinutesUntilDue()
                        >= 118
        );

        assertTrue(
                result.responseMinutesUntilDue()
                        <= 120
        );

        /*
         * Negative means the deadline has passed.
         */
        assertTrue(
                result.resolutionMinutesUntilDue()
                        < 0
        );
    }

    @Test
    void metAndUnknownSlaDoNotExposeMisleadingDeadlineCountdowns() {

        UUID engineerId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        Ticket ticket =
                mock(Ticket.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000053",
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
                ticket.evaluateResponseSla(
                        any(
                                OffsetDateTime.class
                        )
                )
        ).thenReturn(
                SlaStatus.MET
        );

        when(
                ticket.evaluateResolutionSla(
                        any(
                                OffsetDateTime.class
                        )
                )
        ).thenReturn(
                SlaStatus.UNKNOWN
        );

        when(
                ticket.getResponseDueAt()
        ).thenReturn(
                OffsetDateTime.now(
                        ZoneOffset.UTC
                ).minusHours(2)
        );

        when(
                ticket.getResolutionDueAt()
        ).thenReturn(
                null
        );

        AiSupportCopilotSlaSnapshot result =
                toolService
                        .getSlaSnapshot(
                                engineerId,
                                "FD-000053"
                        );

        assertEquals(
                SlaStatus.MET,
                result.responseStatus()
        );

        assertEquals(
                SlaStatus.UNKNOWN,
                result.resolutionStatus()
        );

        assertNull(
                result.responseMinutesUntilDue()
        );

        assertNull(
                result.resolutionMinutesUntilDue()
        );
    }

    @Test
    void toolAccessIsRejectedWithoutCurrentActiveAssignment() {

        UUID engineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000054",
                                engineerId
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                toolService
                                        .getPublicConversation(
                                                engineerId,
                                                "  fd-000054  "
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                "Ticket not found or it is no longer assigned to you",
                exception.getReason()
        );

        verify(
                ticketAssignmentRepository
        ).findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                "FD-000054",
                engineerId
        );

        /*
         * Most importantly, no conversation data is
         * queried after authorization fails.
         */
        verifyNoInteractions(
                ticketCommentRepository
        );
    }
}