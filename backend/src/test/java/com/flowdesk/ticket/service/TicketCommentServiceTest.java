package com.flowdesk.ticket.service;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketComment;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.dto.CreateTicketCommentRequest;
import com.flowdesk.ticket.dto.TicketCommentResponse;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketCommentServiceTest {

    @Mock
    private TicketCommentRepository ticketCommentRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    private TicketCommentService ticketCommentService;

    @BeforeEach
    void setUp() {
        ticketCommentService =
                new TicketCommentService(
                        ticketCommentRepository,
                        ticketRepository,
                        ticketAssignmentRepository,
                        userRepository
                );
    }

    @Test
    void employeeCanReadCommentsForOwnTicket() {

        UUID employeeId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();

        Ticket ticket = org.mockito.Mockito.mock(Ticket.class);
        User author = org.mockito.Mockito.mock(User.class);
        TicketComment comment =
                org.mockito.Mockito.mock(TicketComment.class);

        when(ticket.getId())
                .thenReturn(ticketId);

        when(ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(
                        "FD-000001",
                        employeeId
                ))
                .thenReturn(Optional.of(ticket));

        when(ticketCommentRepository
                .findByTicket_IdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of(comment));

        when(comment.getAuthorUser())
                .thenReturn(author);

        when(comment.getId())
                .thenReturn(UUID.randomUUID());

        when(comment.getBody())
                .thenReturn("Please restart the application.");

        when(comment.getCreatedAt())
                .thenReturn(
                        OffsetDateTime.now(ZoneOffset.UTC)
                );

        when(author.getId())
                .thenReturn(authorId);

        when(author.getFirstName())
                .thenReturn("Support");

        when(author.getLastName())
                .thenReturn("Engineer");

        List<TicketCommentResponse> result =
                ticketCommentService
                        .getEmployeeComments(
                                employeeId,
                                "fd-000001"
                        );

        assertEquals(1, result.size());
        assertEquals(
                "Support Engineer",
                result.getFirst().authorName()
        );
        assertEquals(
                "Please restart the application.",
                result.getFirst().body()
        );
    }

    @Test
    void employeeCannotReadAnotherEmployeesTicket() {

        UUID employeeId = UUID.randomUUID();

        when(ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(
                        "FD-000002",
                        employeeId
                ))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                ticketCommentService
                                        .getEmployeeComments(
                                                employeeId,
                                                "FD-000002"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(
                ticketCommentRepository,
                never()
        ).findByTicket_IdOrderByCreatedAtAsc(any());
    }

    @Test
    void employeeCanAddCommentToOwnActiveTicket() {

        UUID employeeId = UUID.randomUUID();

        Ticket ticket =
                org.mockito.Mockito.mock(Ticket.class);

        User employee =
                org.mockito.Mockito.mock(User.class);

        when(ticket.getStatus())
                .thenReturn(TicketStatus.OPEN);

        when(ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(
                        "FD-000003",
                        employeeId
                ))
                .thenReturn(Optional.of(ticket));

        when(userRepository.findById(employeeId))
                .thenReturn(Optional.of(employee));

        when(employee.getId())
                .thenReturn(employeeId);

        when(employee.getFirstName())
                .thenReturn("Test");

        when(employee.getLastName())
                .thenReturn("Employee");

        when(ticketCommentRepository.saveAndFlush(any()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        CreateTicketCommentRequest request =
                new CreateTicketCommentRequest(
                        "  The issue is still happening.  "
                );

        TicketCommentResponse response =
                ticketCommentService.addEmployeeComment(
                        employeeId,
                        "FD-000003",
                        request
                );

        assertEquals(
                "The issue is still happening.",
                response.body()
        );

        assertEquals(
                "Test Employee",
                response.authorName()
        );

        ArgumentCaptor<TicketComment> captor =
                ArgumentCaptor.forClass(
                        TicketComment.class
                );

        verify(ticketCommentRepository)
                .saveAndFlush(captor.capture());

        assertEquals(
                "The issue is still happening.",
                captor.getValue().getBody()
        );
    }

    @Test
    void assignedSupportEngineerCanReadComments() {

        UUID engineerId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        Ticket ticket =
                org.mockito.Mockito.mock(Ticket.class);

        TicketAssignment assignment =
                org.mockito.Mockito.mock(
                        TicketAssignment.class
                );

        when(ticket.getId())
                .thenReturn(ticketId);

        when(assignment.getTicket())
                .thenReturn(ticket);

        when(ticketAssignmentRepository
                .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                        "FD-000004",
                        engineerId
                ))
                .thenReturn(Optional.of(assignment));

        when(ticketCommentRepository
                .findByTicket_IdOrderByCreatedAtAsc(ticketId))
                .thenReturn(List.of());

        List<TicketCommentResponse> result =
                ticketCommentService
                        .getSupportComments(
                                engineerId,
                                "FD-000004"
                        );

        assertTrue(result.isEmpty());

        verify(ticketCommentRepository)
                .findByTicket_IdOrderByCreatedAtAsc(
                        ticketId
                );
    }

    @Test
    void differentSupportEngineerCannotReadTicketComments() {

        UUID engineerId = UUID.randomUUID();

        when(ticketAssignmentRepository
                .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                        "FD-000005",
                        engineerId
                ))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                ticketCommentService
                                        .getSupportComments(
                                                engineerId,
                                                "FD-000005"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void assignedSupportEngineerCanAddComment() {

        UUID engineerId = UUID.randomUUID();

        Ticket ticket =
                org.mockito.Mockito.mock(Ticket.class);

        TicketAssignment assignment =
                org.mockito.Mockito.mock(
                        TicketAssignment.class
                );

        User engineer =
                org.mockito.Mockito.mock(User.class);

        when(assignment.getTicket())
                .thenReturn(ticket);

        when(ticket.getStatus())
                .thenReturn(TicketStatus.IN_PROGRESS);

        when(ticketAssignmentRepository
                .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                        "FD-000006",
                        engineerId
                ))
                .thenReturn(Optional.of(assignment));

        when(userRepository.findById(engineerId))
                .thenReturn(Optional.of(engineer));

        when(engineer.getId())
                .thenReturn(engineerId);

        when(engineer.getFirstName())
                .thenReturn("Support");

        when(engineer.getLastName())
                .thenReturn("Engineer");

        when(ticketCommentRepository.saveAndFlush(any()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        TicketCommentResponse response =
                ticketCommentService.addSupportComment(
                        engineerId,
                        "FD-000006",
                        new CreateTicketCommentRequest(
                                "Can you confirm the device model?"
                        )
                );

        assertEquals(
                "Can you confirm the device model?",
                response.body()
        );

        assertEquals(
                "Support Engineer",
                response.authorName()
        );
    }

    @Test
    void cannotAddCommentToResolvedTicket() {

        UUID employeeId = UUID.randomUUID();

        Ticket ticket =
                org.mockito.Mockito.mock(Ticket.class);

        when(ticket.getStatus())
                .thenReturn(TicketStatus.RESOLVED);

        when(ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(
                        "FD-000007",
                        employeeId
                ))
                .thenReturn(Optional.of(ticket));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                ticketCommentService
                                        .addEmployeeComment(
                                                employeeId,
                                                "FD-000007",
                                                new CreateTicketCommentRequest(
                                                        "Another message"
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(
                ticketCommentRepository,
                never()
        ).saveAndFlush(any());
    }

    @Test
    void blankCommentIsRejected() {

        UUID employeeId = UUID.randomUUID();

        Ticket ticket =
                org.mockito.Mockito.mock(Ticket.class);

        User employee =
                org.mockito.Mockito.mock(User.class);

        when(ticket.getStatus())
                .thenReturn(TicketStatus.OPEN);

        when(ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(
                        "FD-000008",
                        employeeId
                ))
                .thenReturn(Optional.of(ticket));

        when(userRepository.findById(employeeId))
                .thenReturn(Optional.of(employee));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                ticketCommentService
                                        .addEmployeeComment(
                                                employeeId,
                                                "FD-000008",
                                                new CreateTicketCommentRequest(
                                                        "   "
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(
                ticketCommentRepository,
                never()
        ).saveAndFlush(any());
    }
}