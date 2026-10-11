package com.flowdesk.approval.service;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.ticket.domain.Ticket;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;

import com.flowdesk.user.repository.UserRepository;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ApprovalNotificationService {

    private static final Set<RoleCode>
            APPROVER_ROLES =
            Set.of(
                    RoleCode.TEAM_LEAD,
                    RoleCode.ADMIN
            );

    private final UserRepository
            userRepository;

    private final NotificationService
            notificationService;

    public ApprovalNotificationService(
            UserRepository userRepository,
            NotificationService notificationService
    ) {
        this.userRepository =
                userRepository;

        this.notificationService =
                notificationService;
    }

    @Transactional
    public void notifyApprovalRequested(
            Ticket ticket,
            User requester
    ) {
        Objects.requireNonNull(
                ticket,
                "Approval notification ticket is required"
        );

        Objects.requireNonNull(
                requester,
                "Approval requester is required"
        );

        UUID requesterId =
                Objects.requireNonNull(
                        requester.getId(),
                        "Approval requester must be persisted"
                );

        List<User> approvers =
                userRepository
                        .findDistinctByRoles_CodeInAndStatus(
                                APPROVER_ROLES,
                                UserStatus.ACTIVE
                        );

        for (User approver : approvers) {
            if (approver == null) {
                continue;
            }

            UUID approverId =
                    Objects.requireNonNull(
                            approver.getId(),
                            "Approval notification recipient must be persisted"
                    );

            /*
             * A requester may also hold TEAM_LEAD
             * or ADMIN.
             *
             * Self approval is prohibited, so do not
             * send that user an actionable approval
             * notification for their own request.
             */
            if (requesterId.equals(
                    approverId
            )) {
                continue;
            }

            notificationService
                    .createNotification(
                            approver,
                            requester,
                            ticket,
                            NotificationType
                                    .APPROVAL_REQUESTED,
                            "Approval required",
                            ticket.getTicketNumber()
                                    + " is waiting for approval"
                    );
        }
    }

    @Transactional
    public void notifyApprovalApproved(
            Ticket ticket,
            User requester,
            User approver
    ) {
        notifyRequester(
                ticket,
                requester,
                approver,
                NotificationType.APPROVAL_APPROVED,
                "Service request approved",
                ticket.getTicketNumber()
                        + " was approved"
        );
    }

    @Transactional
    public void notifyApprovalRejected(
            Ticket ticket,
            User requester,
            User approver
    ) {
        notifyRequester(
                ticket,
                requester,
                approver,
                NotificationType.APPROVAL_REJECTED,
                "Service request rejected",
                ticket.getTicketNumber()
                        + " was rejected"
        );
    }

    private void notifyRequester(
            Ticket ticket,
            User requester,
            User approver,
            NotificationType type,
            String title,
            String message
    ) {
        Objects.requireNonNull(
                ticket,
                "Approval notification ticket is required"
        );

        Objects.requireNonNull(
                requester,
                "Approval requester is required"
        );

        Objects.requireNonNull(
                approver,
                "Approval decision maker is required"
        );

        UUID requesterId =
                Objects.requireNonNull(
                        requester.getId(),
                        "Approval requester must be persisted"
                );

        UUID approverId =
                Objects.requireNonNull(
                        approver.getId(),
                        "Approval decision maker must be persisted"
                );

        /*
         * Defensive guard.
         *
         * ApprovalDecisionService already prevents
         * self approval, but the notification layer
         * should not create actor-to-self messages
         * accidentally either.
         */
        if (requesterId.equals(
                approverId
        )) {
            return;
        }

        notificationService
                .createNotification(
                        requester,
                        approver,
                        ticket,
                        type,
                        title,
                        message
                );
    }
}