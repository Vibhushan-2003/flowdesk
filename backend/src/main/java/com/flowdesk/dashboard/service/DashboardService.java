package com.flowdesk.dashboard.service;

import com.flowdesk.approval.domain.ApprovalStatus;
import com.flowdesk.approval.repository.ApprovalRequestRepository;
import com.flowdesk.dashboard.dto.DashboardMetricsResponse;
import com.flowdesk.dashboard.dto.EmployeeMetrics;
import com.flowdesk.dashboard.dto.OperationsMetrics;
import com.flowdesk.dashboard.dto.SupportEngineerMetrics;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final List<TicketStatus> EMPLOYEE_OPEN_STATUSES = List.of(
            TicketStatus.PENDING_APPROVAL,
            TicketStatus.OPEN,
            TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS,
            TicketStatus.WAITING_FOR_USER
    );

    private static final List<TicketStatus> RESOLVED_STATUSES = List.of(
            TicketStatus.RESOLVED,
            TicketStatus.CLOSED
    );

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.OPEN,
            TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS,
            TicketStatus.WAITING_FOR_USER
    );

    private static final List<TicketStatus> RESPONSE_MONITORED_STATUSES = List.of(
            TicketStatus.OPEN,
            TicketStatus.ASSIGNED
    );

    private final TicketRepository ticketRepository;
    private final TicketAssignmentRepository ticketAssignmentRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final UserRepository userRepository;

    public DashboardService(
            TicketRepository ticketRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            ApprovalRequestRepository approvalRequestRepository,
            UserRepository userRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketAssignmentRepository = ticketAssignmentRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DashboardMetricsResponse getMetrics(UUID authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Set<RoleCode> roleCodes = user.getRoles().stream()
                .map(Role::getCode)
                .collect(Collectors.toSet());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 1. Employee metrics (computed for authenticated user)
        long myOpenTickets = ticketRepository.countByCreatedByUser_IdAndStatusIn(
                authenticatedUserId,
                EMPLOYEE_OPEN_STATUSES
        );
        long myResolvedTickets = ticketRepository.countByCreatedByUser_IdAndStatusIn(
                authenticatedUserId,
                RESOLVED_STATUSES
        );
        long ticketsAwaitingResponse = ticketRepository.countByCreatedByUser_IdAndStatus(
                authenticatedUserId,
                TicketStatus.WAITING_FOR_USER
        );

        Long pendingApprovalsCount = null;
        if (roleCodes.contains(RoleCode.TEAM_LEAD) || roleCodes.contains(RoleCode.ADMIN)) {
            pendingApprovalsCount = approvalRequestRepository.countByStatus(ApprovalStatus.PENDING);
        }

        EmployeeMetrics employeeMetrics = new EmployeeMetrics(
                myOpenTickets,
                myResolvedTickets,
                ticketsAwaitingResponse,
                pendingApprovalsCount
        );

        // 2. Support Engineer metrics (only if user has SUPPORT_ENGINEER role)
        SupportEngineerMetrics supportMetrics = null;
        if (roleCodes.contains(RoleCode.SUPPORT_ENGINEER)) {
            long assignedActiveTickets = ticketAssignmentRepository.countActiveAssignments(
                    authenticatedUserId,
                    ACTIVE_STATUSES
            );
            long unassignedQueueTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
            long assignedSlaBreaches = ticketAssignmentRepository.countActiveAssignmentSlaBreaches(
                    authenticatedUserId,
                    now,
                    ACTIVE_STATUSES,
                    RESPONSE_MONITORED_STATUSES
            );
            supportMetrics = new SupportEngineerMetrics(
                    assignedActiveTickets,
                    unassignedQueueTickets,
                    assignedSlaBreaches
            );
        }

        // 3. Operations metrics (only if user has TEAM_LEAD or ADMIN role)
        OperationsMetrics operationsMetrics = null;
        if (roleCodes.contains(RoleCode.TEAM_LEAD) || roleCodes.contains(RoleCode.ADMIN)) {
            long totalActiveTickets = ticketRepository.countByStatusIn(ACTIVE_STATUSES);
            long unassignedQueueTickets = ticketRepository.countByStatus(TicketStatus.OPEN);
            long pendingApprovals = pendingApprovalsCount != null ? pendingApprovalsCount : approvalRequestRepository.countByStatus(ApprovalStatus.PENDING);
            long slaBreaches = ticketRepository.countCurrentActiveSlaBreaches(
                    now,
                    ACTIVE_STATUSES,
                    RESPONSE_MONITORED_STATUSES
            );
            operationsMetrics = new OperationsMetrics(
                    totalActiveTickets,
                    unassignedQueueTickets,
                    pendingApprovals,
                    slaBreaches
            );
        }

        return new DashboardMetricsResponse(
                employeeMetrics,
                supportMetrics,
                operationsMetrics
        );
    }
}

