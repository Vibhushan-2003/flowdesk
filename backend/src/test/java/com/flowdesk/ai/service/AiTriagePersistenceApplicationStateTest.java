package com.flowdesk.ai.service;

import com.flowdesk.ai.domain.AiTriageRecommendation;

import com.flowdesk.ai.dto.AiTriageResponse;

import com.flowdesk.ai.repository.AiTriageRecommendationRepository;

import com.flowdesk.audit.service.AuditService;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTriagePersistenceApplicationStateTest {

    @Test
    void getRecommendationReturnsPersistedApplicationState() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        UUID recommendationId =
                UUID.randomUUID();

        User engineer =
                mock(
                        User.class
                );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        Ticket ticket =
                new Ticket(
                        "FD-000030",
                        TicketType.INCIDENT,
                        "Application state test",
                        "Test persistence after page refresh.",
                        mock(User.class)
                );

        ReflectionTestUtils.setField(
                ticket,
                "id",
                ticketId
        );

        TicketAssignment assignment =
                new TicketAssignment(
                        ticket,
                        engineer
                );

        AiTriageRecommendation recommendation =
                new AiTriageRecommendation(
                        ticket,
                        engineer,
                        TicketPriority.HIGH,
                        new BigDecimal(
                                "0.9500"
                        ),
                        "Important work is blocked.",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite",
                        "triage-v1",
                        OffsetDateTime.parse(
                                "2026-10-13T08:00:00Z"
                        )
                );

        ReflectionTestUtils.setField(
                recommendation,
                "id",
                recommendationId
        );

        OffsetDateTime appliedAt =
                OffsetDateTime.parse(
                        "2026-10-13T08:30:00+00:00"
                );

        recommendation.markApplied(
                engineer,
                appliedAt
        );

        AiTriageRecommendationRepository
                recommendationRepository =
                mock(
                        AiTriageRecommendationRepository.class
                );

        TicketAssignmentRepository
                ticketAssignmentRepository =
                mock(
                        TicketAssignmentRepository.class
                );

        AuditService auditService =
                mock(
                        AuditService.class
                );

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000030",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                recommendationRepository
                        .findByTicket_Id(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        recommendation
                )
        );

        AiTriagePersistenceService service =
                new AiTriagePersistenceService(
                        recommendationRepository,
                        ticketAssignmentRepository,
                        auditService
                );

        AiTriageResponse response =
                service.getRecommendation(
                        engineerId,
                        " fd-000030 "
                );

        assertTrue(
                response.applied()
        );

        assertEquals(
                engineerId,
                response.appliedByUserId()
        );

        assertEquals(
                appliedAt.withOffsetSameInstant(
                        ZoneOffset.UTC
                ),
                response.appliedAt()
        );

        assertEquals(
                recommendationId,
                response.recommendationId()
        );
    }
}