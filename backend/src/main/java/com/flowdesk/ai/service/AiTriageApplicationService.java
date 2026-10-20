package com.flowdesk.ai.service;

import com.flowdesk.ai.domain.AiTriageRecommendation;

import com.flowdesk.ai.dto.AiTriageApplicationResponse;

import com.flowdesk.ai.repository.AiTriageRecommendationRepository;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.sla.domain.SlaPolicy;

import com.flowdesk.sla.repository.SlaEventRepository;
import com.flowdesk.sla.repository.SlaPolicyRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AiTriageApplicationService {

    private final AiTriageRecommendationRepository
            recommendationRepository;

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final SlaPolicyRepository
            slaPolicyRepository;

    private final SlaEventRepository
            slaEventRepository;

    private final AuditService
            auditService;

    public AiTriageApplicationService(
            AiTriageRecommendationRepository recommendationRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            SlaPolicyRepository slaPolicyRepository,
            SlaEventRepository slaEventRepository,
            AuditService auditService
    ) {
        this.recommendationRepository =
                recommendationRepository;

        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.slaPolicyRepository =
                slaPolicyRepository;

        this.slaEventRepository =
                slaEventRepository;

        this.auditService =
                auditService;
    }

    @Transactional
    public AiTriageApplicationResponse
            applyRecommendation(
                    UUID authenticatedUserId,
                    String ticketNumber
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        /*
         * This uses the same pessimistic assignment lock
         * used by support status updates.
         *
         * Therefore:
         *
         * AI priority application
         * and
         * ASSIGNED -> IN_PROGRESS
         *
         * cannot safely modify the ticket concurrently.
         */
        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                normalizedTicketNumber,
                                authenticatedUserId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ticket not found or it is no longer assigned to you"
                                        )
                        );

        User engineer =
                assignment.getAssignedToUser();

        if (engineer == null
                || engineer.getId() == null
                || !engineer.getId()
                        .equals(
                                authenticatedUserId
                        )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ticket is no longer actively assigned to you"
            );
        }

        Ticket ticket =
                assignment.getTicket();

        AiTriageRecommendation recommendation =
                recommendationRepository
                        .findByTicket_Id(
                                ticket.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "AI triage recommendation not found"
                                        )
                        );

        if (recommendation.isApplied()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI triage recommendation has already been applied"
            );
        }

        if (ticket.getStatus()
                != TicketStatus.ASSIGNED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI triage recommendation can only be applied before support work starts"
            );
        }

        TicketPriority previousPriority =
                ticket.getPriority();

        TicketPriority suggestedPriority =
                recommendation
                        .getSuggestedPriority();

        OffsetDateTime previousResponseDueAt =
                ticket.getResponseDueAt();

        OffsetDateTime previousResolutionDueAt =
                ticket.getResolutionDueAt();

        /*
         * If the AI recommendation already matches the
         * current ticket priority, accepting the AI advice
         * is still a meaningful human decision.
         *
         * But we MUST NOT recalculate the SLA because doing
         * so could replace the ticket's existing SLA snapshot
         * with a newer policy version.
         */
        if (previousPriority
                != suggestedPriority) {

            /*
             * Once an SLA breach event has been persisted,
             * its deadline forms part of historical audit
             * evidence.
             *
             * Rewriting the SLA after that point could make
             * that historical event inconsistent.
             */
            if (slaEventRepository
                    .existsByTicket_Id(
                            ticket.getId()
                    )) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "AI triage recommendation cannot change priority after an SLA breach has been recorded"
                );
            }

            SlaPolicy newSlaPolicy =
                    slaPolicyRepository
                            .findByPriorityAndActiveTrue(
                                    suggestedPriority
                            )
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.CONFLICT,
                                                    "No active SLA policy configured for suggested priority "
                                                            + suggestedPriority
                                            )
                            );

            try {
                ticket.applyPriorityAndRecalculateSla(
                        suggestedPriority,
                        newSlaPolicy
                );
            }
            catch (
                    IllegalArgumentException
                    | IllegalStateException exception
            ) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );
            }
        }

        OffsetDateTime appliedAt =
                OffsetDateTime.now(
                        ZoneOffset.UTC
                );

        try {
            recommendation.markApplied(
                    engineer,
                    appliedAt
            );
        }
        catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    exception.getMessage()
            );
        }

        /*
         * saveAndFlush flushes the whole persistence
         * context, including the managed Ticket mutation.
         *
         * The audit record below joins this same transaction.
         * If audit persistence fails, all these changes roll
         * back together.
         */
        recommendationRepository
                .saveAndFlush(
                        recommendation
                );

        auditService.recordUserAction(
                engineer,
                AuditAction.AI_TRIAGE_APPLIED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "recommendationId",
                        recommendation
                                .getId()
                                .toString(),

                        "fromPriority",
                        previousPriority
                                .name(),

                        "toPriority",
                        ticket
                                .getPriority()
                                .name(),

                        "previousResponseDueAt",
                        previousResponseDueAt
                                .toString(),

                        "responseDueAt",
                        ticket
                                .getResponseDueAt()
                                .toString(),

                        "previousResolutionDueAt",
                        previousResolutionDueAt
                                .toString(),

                        "resolutionDueAt",
                        ticket
                                .getResolutionDueAt()
                                .toString()
                )
        );

        return new AiTriageApplicationResponse(
                recommendation.getId(),

                ticket.getId(),

                ticket.getTicketNumber(),

                previousPriority,

                ticket.getPriority(),

                previousResponseDueAt,

                ticket.getResponseDueAt(),

                previousResolutionDueAt,

                ticket.getResolutionDueAt(),

                engineer.getId(),

                recommendation.getAppliedAt()
        );
    }

    private String normalizeTicketNumber(
            String ticketNumber
    ) {

        if (ticketNumber == null
                || ticketNumber.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ticket number is required"
            );
        }

        return ticketNumber
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }
}