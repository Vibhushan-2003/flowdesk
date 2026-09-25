package com.flowdesk.sla.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.service.NotificationService;

import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.sla.repository.SlaEventRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SlaMonitoringService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    SlaMonitoringService.class
            );

    private static final int MONITORING_BATCH_SIZE =
            100;

    private static final List<TicketStatus>
            RESPONSE_MONITORED_STATUSES =
            List.of(
                    TicketStatus.OPEN,
                    TicketStatus.ASSIGNED
            );

    private static final List<TicketStatus>
            RESOLUTION_MONITORED_STATUSES =
            List.of(
                    TicketStatus.OPEN,
                    TicketStatus.ASSIGNED,
                    TicketStatus.IN_PROGRESS,
                    TicketStatus.WAITING_FOR_USER
            );

    private static final Set<RoleCode>
            ESCALATION_ROLES =
            Set.of(
                    RoleCode.TEAM_LEAD,
                    RoleCode.ADMIN
            );

    private final TicketRepository
            ticketRepository;

    private final SlaEventRepository
            slaEventRepository;

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final UserRepository
            userRepository;

    private final NotificationService
            notificationService;

    private final AuditService
            auditService;

    public SlaMonitoringService(
            TicketRepository ticketRepository,
            SlaEventRepository slaEventRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.ticketRepository =
                ticketRepository;

        this.slaEventRepository =
                slaEventRepository;

        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.userRepository =
                userRepository;

        this.notificationService =
                notificationService;

        this.auditService =
                auditService;
    }

    @Transactional
    public int monitorBreaches() {
        return monitorBreaches(
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );
    }

    @Transactional
    int monitorBreaches(
            OffsetDateTime evaluatedAt
    ) {
        Objects.requireNonNull(
                evaluatedAt,
                "SLA evaluation time is required"
        );

        OffsetDateTime normalizedEvaluationTime =
                evaluatedAt
                        .withOffsetSameInstant(
                                ZoneOffset.UTC
                        );

        PageRequest batch =
                PageRequest.of(
                        0,
                        MONITORING_BATCH_SIZE
                );

        int createdEvents = 0;

        List<Ticket> responseBreaches =
                ticketRepository
                        .findResponseSlaBreachCandidates(
                                normalizedEvaluationTime,
                                RESPONSE_MONITORED_STATUSES,
                                SlaEventType.RESPONSE_BREACHED,
                                batch
                        );

        for (Ticket ticket : responseBreaches) {
            createdEvents +=
                    recordAndEscalateBreach(
                            ticket,
                            SlaEventType.RESPONSE_BREACHED,
                            ticket.getResponseDueAt()
                    );
        }

        List<Ticket> resolutionBreaches =
                ticketRepository
                        .findResolutionSlaBreachCandidates(
                                normalizedEvaluationTime,
                                RESOLUTION_MONITORED_STATUSES,
                                SlaEventType.RESOLUTION_BREACHED,
                                batch
                        );

        for (Ticket ticket : resolutionBreaches) {
            createdEvents +=
                    recordAndEscalateBreach(
                            ticket,
                            SlaEventType.RESOLUTION_BREACHED,
                            ticket.getResolutionDueAt()
                    );
        }

        return createdEvents;
    }

    private int recordAndEscalateBreach(
            Ticket ticket,
            SlaEventType eventType,
            OffsetDateTime occurredAt
    ) {
        Objects.requireNonNull(
                ticket,
                "SLA breach ticket is required"
        );

        UUID ticketId =
                Objects.requireNonNull(
                        ticket.getId(),
                        "SLA breach ticket must be persisted"
                );

        OffsetDateTime normalizedOccurredAt =
                Objects.requireNonNull(
                        occurredAt,
                        "SLA breach deadline is required"
                )
                        .withOffsetSameInstant(
                                ZoneOffset.UTC
                        );

        int inserted =
                slaEventRepository
                        .insertIfAbsent(
                                UUID.randomUUID(),
                                ticketId,
                                eventType.name(),
                                normalizedOccurredAt
                        );

        /*
         * Only a newly persisted SLA breach gets an
         * audit entry and escalation.
         *
         * If insertIfAbsent returns 0, another run has
         * already recorded this ticket + breach type.
         */
        if (inserted == 1) {

            auditService.recordSystemAction(
                    auditActionFor(
                            eventType
                    ),
                    AuditTargetType.TICKET,
                    ticketId,
                    ticket.getTicketNumber(),
                    Map.of(
                            "deadline",
                            normalizedOccurredAt.toString(),
                            "eventType",
                            eventType.name()
                    )
            );

            escalateBreach(
                    ticket,
                    eventType
            );
        }

        return inserted;
    }

    private void escalateBreach(
            Ticket ticket,
            SlaEventType eventType
    ) {
        Map<UUID, User> recipients =
                new LinkedHashMap<>();

        ticketAssignmentRepository
                .findByTicket_IdAndReleasedAtIsNull(
                        ticket.getId()
                )
                .map(
                        TicketAssignment::getAssignedToUser
                )
                .ifPresent(
                        user ->
                                addRecipient(
                                        recipients,
                                        user
                                )
                );

        List<User> escalationUsers =
                userRepository
                        .findDistinctByRoles_CodeInAndStatus(
                                ESCALATION_ROLES,
                                UserStatus.ACTIVE
                        );

        escalationUsers.forEach(
                user ->
                        addRecipient(
                                recipients,
                                user
                        )
        );

        if (recipients.isEmpty()) {
            LOGGER.warn(
                    "SLA breach {} for ticket {} has no operational escalation recipients",
                    eventType,
                    ticket.getTicketNumber()
            );

            return;
        }

        NotificationType notificationType =
                notificationTypeFor(
                        eventType
                );

        String title =
                notificationTitleFor(
                        eventType
                );

        String message =
                notificationMessageFor(
                        ticket,
                        eventType
                );

        recipients
                .values()
                .forEach(
                        recipient ->
                                notificationService
                                        .createNotification(
                                                recipient,
                                                null,
                                                ticket,
                                                notificationType,
                                                title,
                                                message
                                        )
                );
    }

    private void addRecipient(
            Map<UUID, User> recipients,
            User user
    ) {
        if (user == null) {
            return;
        }

        UUID userId =
                Objects.requireNonNull(
                        user.getId(),
                        "SLA escalation recipient must be persisted"
                );

        recipients.putIfAbsent(
                userId,
                user
        );
    }

    private AuditAction auditActionFor(
            SlaEventType eventType
    ) {
        return switch (eventType) {
            case RESPONSE_BREACHED ->
                    AuditAction
                            .SLA_RESPONSE_BREACHED;

            case RESOLUTION_BREACHED ->
                    AuditAction
                            .SLA_RESOLUTION_BREACHED;
        };
    }

    private NotificationType notificationTypeFor(
            SlaEventType eventType
    ) {
        return switch (eventType) {
            case RESPONSE_BREACHED ->
                    NotificationType
                            .SLA_RESPONSE_BREACHED;

            case RESOLUTION_BREACHED ->
                    NotificationType
                            .SLA_RESOLUTION_BREACHED;
        };
    }

    private String notificationTitleFor(
            SlaEventType eventType
    ) {
        return switch (eventType) {
            case RESPONSE_BREACHED ->
                    "Response SLA breached";

            case RESOLUTION_BREACHED ->
                    "Resolution SLA breached";
        };
    }

    private String notificationMessageFor(
            Ticket ticket,
            SlaEventType eventType
    ) {
        String ticketNumber =
                ticket.getTicketNumber();

        return switch (eventType) {
            case RESPONSE_BREACHED ->
                    ticketNumber
                            + " missed its response SLA deadline";

            case RESOLUTION_BREACHED ->
                    ticketNumber
                            + " missed its resolution SLA deadline";
        };
    }
}