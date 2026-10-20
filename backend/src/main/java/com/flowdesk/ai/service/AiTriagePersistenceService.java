package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiTriageClientResult;

import com.flowdesk.ai.domain.AiTriageRecommendation;

import com.flowdesk.ai.dto.AiTriageResponse;

import com.flowdesk.ai.repository.AiTriageRecommendationRepository;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AiTriagePersistenceService {

    private final AiTriageRecommendationRepository
            recommendationRepository;

    private final TicketAssignmentRepository
            ticketAssignmentRepository;

    private final AuditService
            auditService;

    public AiTriagePersistenceService(
            AiTriageRecommendationRepository recommendationRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            AuditService auditService
    ) {
        this.recommendationRepository =
                recommendationRepository;

        this.ticketAssignmentRepository =
                ticketAssignmentRepository;

        this.auditService =
                auditService;
    }

    @Transactional(readOnly = true)
    public AiTriageGenerationContext
            prepareGeneration(
                    UUID authenticatedUserId,
                    String ticketNumber
            ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
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

        Ticket ticket =
                assignment.getTicket();

        if (recommendationRepository
                .existsByTicket_Id(
                        ticket.getId()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI triage recommendation already exists"
            );
        }

        return new AiTriageGenerationContext(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getType(),
                ticket.getTitle(),
                ticket.getDescription()
        );
    }

    @Transactional
    public AiTriageResponse persistRecommendation(
            UUID authenticatedUserId,
            UUID ticketId,
            AiTriageClientResult result,
            String promptVersion
    ) {

        Objects.requireNonNull(
                result,
                "AI triage result is required"
        );

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ticket is no longer actively assigned"
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

        if (recommendationRepository
                .existsByTicket_Id(
                        ticket.getId()
                )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI triage recommendation already exists"
            );
        }

        AiTriageRecommendation recommendation =
                new AiTriageRecommendation(
                        ticket,
                        engineer,
                        result.suggestedPriority(),
                        result.confidence(),
                        result.reason(),
                        result.provider(),
                        result.model(),
                        promptVersion,
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        )
                );

        AiTriageRecommendation savedRecommendation;

        try {
            savedRecommendation =
                    recommendationRepository
                            .saveAndFlush(
                                    recommendation
                            );
        }
        catch (DataIntegrityViolationException exception) {

            /*
             * The database UNIQUE(ticket_id)
             * constraint remains the final protection
             * against two concurrent generation requests.
             */
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI triage recommendation already exists"
            );
        }

        auditService.recordUserAction(
                engineer,
                AuditAction.AI_TRIAGE_GENERATED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "suggestedPriority",
                        savedRecommendation
                                .getSuggestedPriority()
                                .name(),

                        "confidence",
                        savedRecommendation
                                .getConfidence()
                                .toPlainString(),

                        "provider",
                        savedRecommendation
                                .getProvider(),

                        "model",
                        savedRecommendation
                                .getModel(),

                        "promptVersion",
                        savedRecommendation
                                .getPromptVersion()
                )
        );

        return toResponse(
                savedRecommendation
        );
    }

    @Transactional(readOnly = true)
    public AiTriageResponse getRecommendation(
            UUID authenticatedUserId,
            String ticketNumber
    ) {

        String normalizedTicketNumber =
                normalizeTicketNumber(
                        ticketNumber
                );

        TicketAssignment assignment =
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
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

        AiTriageRecommendation recommendation =
                recommendationRepository
                        .findByTicket_Id(
                                assignment
                                        .getTicket()
                                        .getId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "AI triage recommendation not found"
                                        )
                        );

        return toResponse(
                recommendation
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

    private AiTriageResponse toResponse(
            AiTriageRecommendation recommendation
    ) {

        Ticket ticket =
                recommendation.getTicket();

        User generatedBy =
                recommendation
                        .getGeneratedByUser();

        User appliedBy =
                recommendation
                        .getAppliedByUser();

        return new AiTriageResponse(
                recommendation.getId(),

                ticket.getId(),

                ticket.getTicketNumber(),

                recommendation
                        .getSuggestedPriority(),

                recommendation
                        .getConfidence(),

                recommendation
                        .getReason(),

                recommendation
                        .getProvider(),

                recommendation
                        .getModel(),

                recommendation
                        .getPromptVersion(),

                generatedBy.getId(),

                recommendation
                        .getCreatedAt(),

                recommendation
                        .isApplied(),

                appliedBy == null
                        ? null
                        : appliedBy.getId(),

                recommendation
                        .getAppliedAt()
        );
    }
}