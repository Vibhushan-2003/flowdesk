package com.flowdesk.ticket.service;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.dto.ClaimTicketResponse;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.dto.TicketSummaryResponse;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User creator;

    private TicketService ticketService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(
                ticketRepository,
                ticketAssignmentRepository,
                userRepository
        );

        userId = UUID.randomUUID();
    }

    @Test
    void createTicketShouldGenerateDefaultsAndPersistTicket() {
        CreateTicketRequest request =
                new CreateTicketRequest(
                        TicketType.INCIDENT,
                        "  VPN is not working  ",
                        "  Cannot connect to company VPN.  "
                );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(creator));

        when(creator.getId())
                .thenReturn(userId);

        when(creator.getEmail())
                .thenReturn("employee@example.com");

        when(ticketRepository.getNextTicketNumberSequenceValue())
                .thenReturn(3L);

        when(ticketRepository.saveAndFlush(any(Ticket.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        TicketResponse response =
                ticketService.createTicket(
                        userId,
                        request
                );

        assertEquals(
                "FD-000003",
                response.ticketNumber()
        );

        assertEquals(
                TicketType.INCIDENT,
                response.type()
        );

        assertEquals(
                "VPN is not working",
                response.title()
        );

        assertEquals(
                "Cannot connect to company VPN.",
                response.description()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                response.priority()
        );

        assertEquals(
                TicketStatus.OPEN,
                response.status()
        );

        assertEquals(
                userId,
                response.createdByUserId()
        );

        assertEquals(
                "employee@example.com",
                response.createdByEmail()
        );

        ArgumentCaptor<Ticket> ticketCaptor =
                ArgumentCaptor.forClass(Ticket.class);

        verify(ticketRepository)
                .saveAndFlush(ticketCaptor.capture());

        Ticket savedTicket =
                ticketCaptor.getValue();

        assertEquals(
                "FD-000003",
                savedTicket.getTicketNumber()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                savedTicket.getPriority()
        );

        assertEquals(
                TicketStatus.OPEN,
                savedTicket.getStatus()
        );

        assertSame(
                creator,
                savedTicket.getCreatedByUser()
        );

        verify(userRepository)
                .findById(userId);

        verify(ticketRepository)
                .getNextTicketNumberSequenceValue();
    }

    @Test
    void getMyTicketsShouldReturnPaginatedTicketSummaries() {
        Ticket newestTicket = mock(Ticket.class);
        Ticket olderTicket = mock(Ticket.class);

        UUID newestTicketId = UUID.randomUUID();
        UUID olderTicketId = UUID.randomUUID();

        OffsetDateTime newestCreatedAt =
                OffsetDateTime.parse(
                        "2026-09-12T05:20:47Z"
                );

        OffsetDateTime olderCreatedAt =
                OffsetDateTime.parse(
                        "2026-09-12T05:00:31Z"
                );

        when(newestTicket.getId())
                .thenReturn(newestTicketId);

        when(newestTicket.getTicketNumber())
                .thenReturn("FD-000002");

        when(newestTicket.getType())
                .thenReturn(TicketType.SERVICE_REQUEST);

        when(newestTicket.getTitle())
                .thenReturn(
                        "Request GitHub repository access"
                );

        when(newestTicket.getPriority())
                .thenReturn(TicketPriority.MEDIUM);

        when(newestTicket.getStatus())
                .thenReturn(TicketStatus.OPEN);

        when(newestTicket.getCreatedAt())
                .thenReturn(newestCreatedAt);

        when(newestTicket.getUpdatedAt())
                .thenReturn(newestCreatedAt);

        when(olderTicket.getId())
                .thenReturn(olderTicketId);

        when(olderTicket.getTicketNumber())
                .thenReturn("FD-000001");

        when(olderTicket.getType())
                .thenReturn(TicketType.INCIDENT);

        when(olderTicket.getTitle())
                .thenReturn("VPN is not working");

        when(olderTicket.getPriority())
                .thenReturn(TicketPriority.MEDIUM);

        when(olderTicket.getStatus())
                .thenReturn(TicketStatus.OPEN);

        when(olderTicket.getCreatedAt())
                .thenReturn(olderCreatedAt);

        when(olderTicket.getUpdatedAt())
                .thenReturn(olderCreatedAt);

        when(
                ticketRepository.findByCreatedByUser_Id(
                        eq(userId),
                        any(Pageable.class)
                )
        ).thenReturn(
                new PageImpl<>(
                        List.of(
                                newestTicket,
                                olderTicket
                        ),
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                10
                        ),
                        2
                )
        );

        PageResponse<TicketSummaryResponse> response =
                ticketService.getMyTickets(
                        userId,
                        0,
                        10
                );

        assertEquals(
                2,
                response.content().size()
        );

        assertEquals(
                "FD-000002",
                response.content()
                        .get(0)
                        .ticketNumber()
        );

        assertEquals(
                "FD-000001",
                response.content()
                        .get(1)
                        .ticketNumber()
        );

        assertEquals(
                0,
                response.page()
        );

        assertEquals(
                10,
                response.size()
        );

        assertEquals(
                2,
                response.totalElements()
        );

        assertEquals(
                1,
                response.totalPages()
        );

        assertEquals(
                true,
                response.first()
        );

        assertEquals(
                true,
                response.last()
        );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(ticketRepository)
                .findByCreatedByUser_Id(
                        eq(userId),
                        pageableCaptor.capture()
                );

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertEquals(
                0,
                capturedPageable.getPageNumber()
        );

        assertEquals(
                10,
                capturedPageable.getPageSize()
        );

        assertEquals(
                org.springframework.data.domain.Sort.Direction.DESC,
                capturedPageable
                        .getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
    }

    @Test
    void getMyTicketShouldReturnOwnedTicketAndNormalizeTicketNumber() {
        Ticket ticket = mock(Ticket.class);

        UUID ticketId = UUID.randomUUID();

        OffsetDateTime createdAt =
                OffsetDateTime.parse(
                        "2026-09-12T05:20:47Z"
                );

        when(
                ticketRepository
                        .findByTicketNumberAndCreatedByUser_Id(
                                "FD-000002",
                                userId
                        )
        ).thenReturn(Optional.of(ticket));

        when(ticket.getId())
                .thenReturn(ticketId);

        when(ticket.getTicketNumber())
                .thenReturn("FD-000002");

        when(ticket.getType())
                .thenReturn(TicketType.SERVICE_REQUEST);

        when(ticket.getTitle())
                .thenReturn(
                        "Request GitHub repository access"
                );

        when(ticket.getDescription())
                .thenReturn(
                        "I need access to the development repository."
                );

        when(ticket.getPriority())
                .thenReturn(TicketPriority.MEDIUM);

        when(ticket.getStatus())
                .thenReturn(TicketStatus.OPEN);

        when(ticket.getCreatedByUser())
                .thenReturn(creator);

        when(creator.getId())
                .thenReturn(userId);

        when(creator.getEmail())
                .thenReturn("employee@example.com");

        when(ticket.getCreatedAt())
                .thenReturn(createdAt);

        when(ticket.getUpdatedAt())
                .thenReturn(createdAt);

        TicketResponse response =
                ticketService.getMyTicket(
                        userId,
                        "fd-000002"
                );

        assertEquals(
                "FD-000002",
                response.ticketNumber()
        );

        assertEquals(
                "Request GitHub repository access",
                response.title()
        );

        assertEquals(
                userId,
                response.createdByUserId()
        );

        verify(ticketRepository)
                .findByTicketNumberAndCreatedByUser_Id(
                        "FD-000002",
                        userId
                );
    }

    @Test
    void getMyTicketShouldReturnNotFoundWhenTicketIsNotOwned() {
        when(
                ticketRepository
                        .findByTicketNumberAndCreatedByUser_Id(
                                "FD-000002",
                                userId
                        )
        ).thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ticketService.getMyTicket(
                                userId,
                                "FD-000002"
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(ticketRepository)
                .findByTicketNumberAndCreatedByUser_Id(
                        "FD-000002",
                        userId
                );
    }

    @Test
    void getSupportQueueShouldReturnOpenTicketsOldestFirst() {
        Ticket olderTicket = mock(Ticket.class);
        Ticket newerTicket = mock(Ticket.class);

        UUID olderTicketId = UUID.randomUUID();
        UUID newerTicketId = UUID.randomUUID();

        OffsetDateTime olderCreatedAt =
                OffsetDateTime.parse(
                        "2026-09-12T05:00:31Z"
                );

        OffsetDateTime newerCreatedAt =
                OffsetDateTime.parse(
                        "2026-09-12T05:20:47Z"
                );

        when(olderTicket.getId())
                .thenReturn(olderTicketId);

        when(olderTicket.getTicketNumber())
                .thenReturn("FD-000001");

        when(olderTicket.getType())
                .thenReturn(TicketType.INCIDENT);

        when(olderTicket.getTitle())
                .thenReturn("VPN is not working");

        when(olderTicket.getPriority())
                .thenReturn(TicketPriority.MEDIUM);

        when(olderTicket.getStatus())
                .thenReturn(TicketStatus.OPEN);

        when(olderTicket.getCreatedAt())
                .thenReturn(olderCreatedAt);

        when(olderTicket.getUpdatedAt())
                .thenReturn(olderCreatedAt);

        when(newerTicket.getId())
                .thenReturn(newerTicketId);

        when(newerTicket.getTicketNumber())
                .thenReturn("FD-000002");

        when(newerTicket.getType())
                .thenReturn(TicketType.SERVICE_REQUEST);

        when(newerTicket.getTitle())
                .thenReturn(
                        "Request GitHub repository access"
                );

        when(newerTicket.getPriority())
                .thenReturn(TicketPriority.MEDIUM);

        when(newerTicket.getStatus())
                .thenReturn(TicketStatus.OPEN);

        when(newerTicket.getCreatedAt())
                .thenReturn(newerCreatedAt);

        when(newerTicket.getUpdatedAt())
                .thenReturn(newerCreatedAt);

        when(
                ticketRepository.findByStatus(
                        eq(TicketStatus.OPEN),
                        any(Pageable.class)
                )
        ).thenReturn(
                new PageImpl<>(
                        List.of(
                                olderTicket,
                                newerTicket
                        ),
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                10
                        ),
                        2
                )
        );

        PageResponse<TicketSummaryResponse> response =
                ticketService.getSupportQueue(
                        0,
                        10
                );

        assertEquals(
                2,
                response.content().size()
        );

        assertEquals(
                "FD-000001",
                response.content()
                        .get(0)
                        .ticketNumber()
        );

        assertEquals(
                "FD-000002",
                response.content()
                        .get(1)
                        .ticketNumber()
        );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(ticketRepository)
                .findByStatus(
                        eq(TicketStatus.OPEN),
                        pageableCaptor.capture()
                );

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertEquals(
                0,
                capturedPageable.getPageNumber()
        );

        assertEquals(
                10,
                capturedPageable.getPageSize()
        );

        assertEquals(
                org.springframework.data.domain.Sort.Direction.ASC,
                capturedPageable
                        .getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
    }

    @Test
    void claimTicketShouldAssignOpenTicketToSupportEngineer() {
        User engineer = mock(User.class);
        Role supportEngineerRole = mock(Role.class);

        UUID engineerId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        OffsetDateTime assignedAt =
                OffsetDateTime.parse(
                        "2026-09-14T14:00:00Z"
                );

        when(userRepository.findById(engineerId))
                .thenReturn(Optional.of(engineer));

        when(engineer.getRoles())
                .thenReturn(
                        Set.of(supportEngineerRole)
                );

        when(supportEngineerRole.getCode())
                .thenReturn(
                        RoleCode.SUPPORT_ENGINEER
                );

        when(engineer.getId())
                .thenReturn(engineerId);

        when(engineer.getEmail())
                .thenReturn(
                        "engineer@example.com"
                );

        Ticket ticket = new Ticket(
                "FD-000001",
                TicketType.INCIDENT,
                "VPN is not working",
                "Cannot connect to company VPN.",
                creator
        );

        when(
                ticketRepository.findByTicketNumberForUpdate(
                        "FD-000001"
                )
        ).thenReturn(
                Optional.of(ticket)
        );

        TicketAssignment savedAssignment =
                mock(TicketAssignment.class);

        when(savedAssignment.getId())
                .thenReturn(assignmentId);

        when(savedAssignment.getAssignedAt())
                .thenReturn(assignedAt);

        when(
                ticketAssignmentRepository.saveAndFlush(
                        any(TicketAssignment.class)
                )
        ).thenReturn(savedAssignment);

        ClaimTicketResponse response =
                ticketService.claimTicket(
                        engineerId,
                        "fd-000001"
                );

        assertEquals(
                "FD-000001",
                response.ticketNumber()
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                response.status()
        );

        assertEquals(
                assignmentId,
                response.assignmentId()
        );

        assertEquals(
                engineerId,
                response.assignedToUserId()
        );

        assertEquals(
                "engineer@example.com",
                response.assignedToEmail()
        );

        assertEquals(
                assignedAt,
                response.assignedAt()
        );

        assertEquals(
                TicketStatus.ASSIGNED,
                ticket.getStatus()
        );

        verify(ticketRepository)
                .findByTicketNumberForUpdate(
                        "FD-000001"
                );

        ArgumentCaptor<TicketAssignment> assignmentCaptor =
                ArgumentCaptor.forClass(
                        TicketAssignment.class
                );

        verify(ticketAssignmentRepository)
                .saveAndFlush(
                        assignmentCaptor.capture()
                );

        TicketAssignment createdAssignment =
                assignmentCaptor.getValue();

        assertSame(
                ticket,
                createdAssignment.getTicket()
        );

        assertSame(
                engineer,
                createdAssignment.getAssignedToUser()
        );
    }

    @Test
    void claimTicketShouldReturnConflictWhenTicketIsNotOpen() {
        User engineer = mock(User.class);
        Role supportEngineerRole = mock(Role.class);
        Ticket ticket = mock(Ticket.class);

        UUID engineerId = UUID.randomUUID();

        when(userRepository.findById(engineerId))
                .thenReturn(Optional.of(engineer));

        when(engineer.getRoles())
                .thenReturn(
                        Set.of(supportEngineerRole)
                );

        when(supportEngineerRole.getCode())
                .thenReturn(
                        RoleCode.SUPPORT_ENGINEER
                );

        when(
                ticketRepository.findByTicketNumberForUpdate(
                        "FD-000002"
                )
        ).thenReturn(
                Optional.of(ticket)
        );

        when(ticket.getStatus())
                .thenReturn(TicketStatus.ASSIGNED);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ticketService.claimTicket(
                                engineerId,
                                "fd-000002"
                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verify(ticketRepository)
                .findByTicketNumberForUpdate(
                        "FD-000002"
                );

        verify(
                ticketAssignmentRepository,
                never()
        ).saveAndFlush(
                any(TicketAssignment.class)
        );
    }

    @Test
    void claimTicketShouldReturnForbiddenForNonSupportUser() {
        User employee = mock(User.class);
        Role employeeRole = mock(Role.class);

        UUID employeeId = UUID.randomUUID();

        when(userRepository.findById(employeeId))
                .thenReturn(Optional.of(employee));

        when(employee.getRoles())
                .thenReturn(
                        Set.of(employeeRole)
                );

        when(employeeRole.getCode())
                .thenReturn(RoleCode.EMPLOYEE);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ticketService.claimTicket(
                                employeeId,
                                "FD-000001"
                        )
                );

        assertEquals(
                HttpStatus.FORBIDDEN,
                exception.getStatusCode()
        );

        verify(userRepository)
                .findById(employeeId);

        verify(
                ticketRepository,
                never()
        ).findByTicketNumberForUpdate(
                anyString()
        );

        verify(
                ticketAssignmentRepository,
                never()
        ).saveAndFlush(
                any(TicketAssignment.class)
        );
    }
}