package com.flowdesk.approval.service;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;

import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalNotificationServiceTest {

    @Mock
    private UserRepository
            userRepository;

    @Mock
    private NotificationService
            notificationService;

    private ApprovalNotificationService
            service;

    @BeforeEach
    void setUp() {
        service =
                new ApprovalNotificationService(
                        userRepository,
                        notificationService
                );
    }

    @Test
    void approvalRequestedNotifiesApproversButNotRequester() {

        UUID requesterId =
                UUID.randomUUID();

        User requester =
                user(
                        requesterId
                );

        User teamLead =
                user(
                        UUID.randomUUID()
                );

        User admin =
                user(
                        UUID.randomUUID()
                );

        Ticket ticket =
                serviceRequest(
                        requester,
                        "FD-000040"
                );

        when(
                userRepository
                        .findDistinctByRoles_CodeInAndStatus(
                                Set.of(
                                        RoleCode.TEAM_LEAD,
                                        RoleCode.ADMIN
                                ),
                                UserStatus.ACTIVE
                        )
        ).thenReturn(
                List.of(
                        requester,
                        teamLead,
                        admin
                )
        );

        service.notifyApprovalRequested(
                ticket,
                requester
        );

        verify(
                notificationService
        ).createNotification(
                teamLead,
                requester,
                ticket,
                NotificationType
                        .APPROVAL_REQUESTED,
                "Approval required",
                "FD-000040 is waiting for approval"
        );

        verify(
                notificationService
        ).createNotification(
                admin,
                requester,
                ticket,
                NotificationType
                        .APPROVAL_REQUESTED,
                "Approval required",
                "FD-000040 is waiting for approval"
        );

        verify(
                notificationService,
                never()
        ).createNotification(
                requester,
                requester,
                ticket,
                NotificationType
                        .APPROVAL_REQUESTED,
                "Approval required",
                "FD-000040 is waiting for approval"
        );
    }

    @Test
    void approvalApprovedNotifiesRequester() {

        User requester =
                user(
                        UUID.randomUUID()
                );

        User approver =
                user(
                        UUID.randomUUID()
                );

        Ticket ticket =
                serviceRequest(
                        requester,
                        "FD-000041"
                );

        service.notifyApprovalApproved(
                ticket,
                requester,
                approver
        );

        verify(
                notificationService
        ).createNotification(
                requester,
                approver,
                ticket,
                NotificationType
                        .APPROVAL_APPROVED,
                "Service request approved",
                "FD-000041 was approved"
        );
    }

    @Test
    void approvalRejectedNotifiesRequester() {

        User requester =
                user(
                        UUID.randomUUID()
                );

        User approver =
                user(
                        UUID.randomUUID()
                );

        Ticket ticket =
                serviceRequest(
                        requester,
                        "FD-000042"
                );

        service.notifyApprovalRejected(
                ticket,
                requester,
                approver
        );

        verify(
                notificationService
        ).createNotification(
                requester,
                approver,
                ticket,
                NotificationType
                        .APPROVAL_REJECTED,
                "Service request rejected",
                "FD-000042 was rejected"
        );
    }

    private Ticket serviceRequest(
            User requester,
            String ticketNumber
    ) {
        return new Ticket(
                ticketNumber,
                TicketType.SERVICE_REQUEST,
                "Software access request",
                "Access is required for work.",
                requester
        );
    }

    private User user(
            UUID id
    ) {
        User user =
                org.mockito.Mockito.mock(
                        User.class
                );

        when(
                user.getId()
        ).thenReturn(
                id
        );

        return user;
    }
}