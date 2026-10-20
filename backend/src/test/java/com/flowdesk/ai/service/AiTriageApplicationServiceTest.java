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
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTriageApplicationServiceTest {

    private static final UUID ENGINEER_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID TICKET_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID RECOMMENDATION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private static final OffsetDateTime
            ORIGINAL_SLA_START =
            OffsetDateTime.parse(
                    "2026-10-13T04:00:00Z"
            );

    private AiTriageRecommendationRepository
            recommendationRepository;

    private TicketAssignmentRepository
            ticketAssignmentRepository;

    private SlaPolicyRepository
            slaPolicyRepository;

    private SlaEventRepository
            slaEventRepository;

    private AuditService
            auditService;

    private AiTriageApplicationService
            service;

    private User engineer;

    @BeforeEach
    void setUp() {

        recommendationRepository =
                mock(
                        AiTriageRecommendationRepository.class
                );

        ticketAssignmentRepository =
                mock(
                        TicketAssignmentRepository.class
                );

        slaPolicyRepository =
                mock(
                        SlaPolicyRepository.class
                );

        slaEventRepository =
                mock(
                        SlaEventRepository.class
                );

        auditService =
                mock(
                        AuditService.class
                );

        engineer =
                mock(
                        User.class
                );

        when(
                engineer.getId()
        )
                .thenReturn(
                        ENGINEER_ID
                );

        service =
                new AiTriageApplicationService(
                        recommendationRepository,
                        ticketAssignmentRepository,
                        slaPolicyRepository,
                        slaEventRepository,
                        auditService
                );
    }

    @Test
    void recommendationCanBeAppliedAndSlaStartIsPreserved() {

        Ticket ticket =
                createAssignedTicket();

        AiTriageRecommendation recommendation =
                createRecommendation(
                        ticket,
                        TicketPriority.HIGH
                );

        prepareActiveAssignment(
                ticket
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        Optional.of(
                                recommendation
                        )
                );

        when(
                slaEventRepository
                        .existsByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        false
                );

        SlaPolicy highPolicy =
                new SlaPolicy(
                        TicketPriority.HIGH,
                        30,
                        480
                );

        when(
                slaPolicyRepository
                        .findByPriorityAndActiveTrue(
                                TicketPriority.HIGH
                        )
        )
                .thenReturn(
                        Optional.of(
                                highPolicy
                        )
                );

        when(
                recommendationRepository
                        .saveAndFlush(
                                recommendation
                        )
        )
                .thenReturn(
                        recommendation
                );

        AiTriageApplicationResponse response =
                service.applyRecommendation(
                        ENGINEER_ID,
                        " fd-test-apply "
                );

        assertEquals(
                TicketPriority.HIGH,
                ticket.getPriority()
        );

        assertSame(
                highPolicy,
                ticket.getSlaPolicy()
        );

        assertEquals(
                ORIGINAL_SLA_START
                        .plusMinutes(
                                30
                        ),
                ticket.getResponseDueAt()
        );

        assertEquals(
                ORIGINAL_SLA_START
                        .plusMinutes(
                                480
                        ),
                ticket.getResolutionDueAt()
        );

        assertTrue(
                recommendation.isApplied()
        );

        assertSame(
                engineer,
                recommendation
                        .getAppliedByUser()
        );

        assertEquals(
                RECOMMENDATION_ID,
                response.recommendationId()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                response.previousPriority()
        );

        assertEquals(
                TicketPriority.HIGH,
                response.appliedPriority()
        );

        verify(
                ticketAssignmentRepository
        )
                .findActiveAssignmentForUpdate(
                        "FD-TEST-APPLY",
                        ENGINEER_ID
                );

        verify(
                auditService
        )
                .recordUserAction(
                        same(
                                engineer
                        ),
                        eq(
                                AuditAction
                                        .AI_TRIAGE_APPLIED
                        ),
                        eq(
                                AuditTargetType.TICKET
                        ),
                        eq(
                                TICKET_ID
                        ),
                        eq(
                                "FD-TEST-APPLY"
                        ),
                        anyMap()
                );
    }

    @Test
    void matchingPriorityCanBeAcceptedWithoutRecalculatingSla() {

        Ticket ticket =
                createAssignedTicket();

        OffsetDateTime originalResponseDueAt =
                ticket.getResponseDueAt();

        OffsetDateTime originalResolutionDueAt =
                ticket.getResolutionDueAt();

        AiTriageRecommendation recommendation =
                createRecommendation(
                        ticket,
                        TicketPriority.MEDIUM
                );

        prepareActiveAssignment(
                ticket
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        Optional.of(
                                recommendation
                        )
                );

        when(
                recommendationRepository
                        .saveAndFlush(
                                recommendation
                        )
        )
                .thenReturn(
                        recommendation
                );

        AiTriageApplicationResponse response =
                service.applyRecommendation(
                        ENGINEER_ID,
                        "FD-TEST-APPLY"
                );

        assertEquals(
                TicketPriority.MEDIUM,
                ticket.getPriority()
        );

        assertEquals(
                originalResponseDueAt,
                ticket.getResponseDueAt()
        );

        assertEquals(
                originalResolutionDueAt,
                ticket.getResolutionDueAt()
        );

        assertTrue(
                recommendation.isApplied()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                response.previousPriority()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                response.appliedPriority()
        );

        verify(
                slaPolicyRepository,
                never()
        )
                .findByPriorityAndActiveTrue(
                        TicketPriority.MEDIUM
                );

        verify(
                slaEventRepository,
                never()
        )
                .existsByTicket_Id(
                        TICKET_ID
                );
    }

    @Test
    void missingActiveAssignmentReturnsNotFound() {

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-MISSING",
                                ENGINEER_ID
                        )
        )
                .thenReturn(
                        Optional.empty()
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.applyRecommendation(
                                        ENGINEER_ID,
                                        "FD-MISSING"
                                )
                );

        assertEquals(
                404,
                exception
                        .getStatusCode()
                        .value()
        );
    }

    @Test
    void missingRecommendationReturnsNotFound() {

        Ticket ticket =
                createAssignedTicket();

        prepareActiveAssignment(
                ticket
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        Optional.empty()
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.applyRecommendation(
                                        ENGINEER_ID,
                                        "FD-TEST-APPLY"
                                )
                );

        assertEquals(
                404,
                exception
                        .getStatusCode()
                        .value()
        );
    }

    @Test
    void alreadyAppliedRecommendationReturnsConflict() {

        Ticket ticket =
                createAssignedTicket();

        AiTriageRecommendation recommendation =
                createRecommendation(
                        ticket,
                        TicketPriority.HIGH
                );

        recommendation.markApplied(
                engineer,
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );

        prepareActiveAssignment(
                ticket
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        Optional.of(
                                recommendation
                        )
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.applyRecommendation(
                                        ENGINEER_ID,
                                        "FD-TEST-APPLY"
                                )
                );

        assertEquals(
                409,
                exception
                        .getStatusCode()
                        .value()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                ticket.getPriority()
        );
    }

    @Test
    void priorityChangeAfterRecordedSlaBreachIsRejected() {

        Ticket ticket =
                createAssignedTicket();

        AiTriageRecommendation recommendation =
                createRecommendation(
                        ticket,
                        TicketPriority.HIGH
                );

        prepareActiveAssignment(
                ticket
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        Optional.of(
                                recommendation
                        )
                );

        when(
                slaEventRepository
                        .existsByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        true
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.applyRecommendation(
                                        ENGINEER_ID,
                                        "FD-TEST-APPLY"
                                )
                );

        assertEquals(
                409,
                exception
                        .getStatusCode()
                        .value()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                ticket.getPriority()
        );

        assertFalse(
                recommendation.isApplied()
        );

        verify(
                slaPolicyRepository,
                never()
        )
                .findByPriorityAndActiveTrue(
                        TicketPriority.HIGH
                );
    }

    @Test
    void missingSlaPolicyReturnsConflict() {

        Ticket ticket =
                createAssignedTicket();

        AiTriageRecommendation recommendation =
                createRecommendation(
                        ticket,
                        TicketPriority.HIGH
                );

        prepareActiveAssignment(
                ticket
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        Optional.of(
                                recommendation
                        )
                );

        when(
                slaEventRepository
                        .existsByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        false
                );

        when(
                slaPolicyRepository
                        .findByPriorityAndActiveTrue(
                                TicketPriority.HIGH
                        )
        )
                .thenReturn(
                        Optional.empty()
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.applyRecommendation(
                                        ENGINEER_ID,
                                        "FD-TEST-APPLY"
                                )
                );

        assertEquals(
                409,
                exception
                        .getStatusCode()
                        .value()
        );

        assertFalse(
                recommendation.isApplied()
        );
    }

    @Test
    void recommendationCannotBeAppliedAfterSupportWorkStarts() {

        Ticket ticket =
                createAssignedTicket();

        ticket.transitionSupportStatus(
                TicketStatus.IN_PROGRESS
        );

        AiTriageRecommendation recommendation =
                createRecommendation(
                        ticket,
                        TicketPriority.HIGH
                );

        prepareActiveAssignment(
                ticket
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                TICKET_ID
                        )
        )
                .thenReturn(
                        Optional.of(
                                recommendation
                        )
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.applyRecommendation(
                                        ENGINEER_ID,
                                        "FD-TEST-APPLY"
                                )
                );

        assertEquals(
                409,
                exception
                        .getStatusCode()
                        .value()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                ticket.getPriority()
        );

        assertFalse(
                recommendation.isApplied()
        );

        verify(
                slaEventRepository,
                never()
        )
                .existsByTicket_Id(
                        TICKET_ID
                );
    }

    @Test
    void blankTicketNumberReturnsBadRequest() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.applyRecommendation(
                                        ENGINEER_ID,
                                        "   "
                                )
                );

        assertEquals(
                400,
                exception
                        .getStatusCode()
                        .value()
        );
    }

    private void prepareActiveAssignment(
            Ticket ticket
    ) {

        TicketAssignment assignment =
                new TicketAssignment(
                        ticket,
                        engineer
                );

        when(
                ticketAssignmentRepository
                        .findActiveAssignmentForUpdate(
                                "FD-TEST-APPLY",
                                ENGINEER_ID
                        )
        )
                .thenReturn(
                        Optional.of(
                                assignment
                        )
                );
    }

    private Ticket createAssignedTicket() {

        User creator =
                mock(
                        User.class
                );

        Ticket ticket =
                new Ticket(
                        "FD-TEST-APPLY",
                        TicketType.INCIDENT,
                        "Test AI application",
                        "Test AI recommendation application",
                        creator
                );

        ReflectionTestUtils.setField(
                ticket,
                "id",
                TICKET_ID
        );

        ticket.applySlaPolicy(
                new SlaPolicy(
                        TicketPriority.MEDIUM,
                        60,
                        1440
                ),
                ORIGINAL_SLA_START
        );

        ticket.markAssigned();

        return ticket;
    }

    private AiTriageRecommendation
            createRecommendation(
                    Ticket ticket,
                    TicketPriority suggestedPriority
            ) {

        AiTriageRecommendation recommendation =
                new AiTriageRecommendation(
                        ticket,
                        engineer,
                        suggestedPriority,
                        new BigDecimal(
                                "0.9500"
                        ),
                        "The issue has significant business impact.",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite",
                        "triage-v1",
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        )
                );

        ReflectionTestUtils.setField(
                recommendation,
                "id",
                RECOMMENDATION_ID
        );

        return recommendation;
    }
}