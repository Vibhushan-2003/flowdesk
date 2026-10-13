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
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.ticket.repository.TicketAssignmentRepository;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.http.HttpStatus;

import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTriagePersistenceServiceTest {

    @Mock
    private AiTriageRecommendationRepository
            recommendationRepository;

    @Mock
    private TicketAssignmentRepository
            ticketAssignmentRepository;

    @Mock
    private AuditService
            auditService;

    private AiTriagePersistenceService
            persistenceService;

    @BeforeEach
    void setUp() {

        persistenceService =
                new AiTriagePersistenceService(
                        recommendationRepository,
                        ticketAssignmentRepository,
                        auditService
                );
    }

    @Test
    void prepareGenerationReturnsOnlyAssignedTicketData() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

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
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000030"
        );

        when(
                ticket.getType()
        ).thenReturn(
                TicketType.INCIDENT
        );

        when(
                ticket.getTitle()
        ).thenReturn(
                "VPN outage"
        );

        when(
                ticket.getDescription()
        ).thenReturn(
                "Cannot connect to VPN"
        );

        when(
                recommendationRepository
                        .existsByTicket_Id(
                                ticketId
                        )
        ).thenReturn(
                false
        );

        AiTriageGenerationContext context =
                persistenceService
                        .prepareGeneration(
                                engineerId,
                                "  fd-000030  "
                        );

        assertEquals(
                ticketId,
                context.ticketId()
        );

        assertEquals(
                "FD-000030",
                context.ticketNumber()
        );

        assertEquals(
                TicketType.INCIDENT,
                context.type()
        );

        assertEquals(
                "VPN outage",
                context.title()
        );

        assertEquals(
                "Cannot connect to VPN",
                context.description()
        );
    }

    @Test
    void prepareGenerationRejectsTicketWithoutActiveAssignment() {

        UUID engineerId =
                UUID.randomUUID();

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000031",
                                engineerId
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                persistenceService
                                        .prepareGeneration(
                                                engineerId,
                                                "FD-000031"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                recommendationRepository,
                auditService
        );
    }

    @Test
    void prepareGenerationRejectsExistingRecommendation() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        Ticket ticket =
                mock(Ticket.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000032",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                recommendationRepository
                        .existsByTicket_Id(
                                ticketId
                        )
        ).thenReturn(
                true
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                persistenceService
                                        .prepareGeneration(
                                                engineerId,
                                                "FD-000032"
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );
    }

    @Test
    void persistRecommendationStoresResultAndAuditAtomically() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        UUID recommendationId =
                UUID.randomUUID();

        Ticket ticket =
                mock(Ticket.class);

        User engineer =
                mock(User.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                engineer
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000033"
        );

        when(
                recommendationRepository
                        .existsByTicket_Id(
                                ticketId
                        )
        ).thenReturn(
                false
        );

        when(
                recommendationRepository
                        .saveAndFlush(
                                any(
                                        AiTriageRecommendation.class
                                )
                        )
        ).thenAnswer(
                invocation -> {

                    AiTriageRecommendation recommendation =
                            invocation.getArgument(
                                    0
                            );

                    ReflectionTestUtils.setField(
                            recommendation,
                            "id",
                            recommendationId
                    );

                    return recommendation;
                }
        );

        AiTriageClientResult result =
                new AiTriageClientResult(
                        TicketPriority.HIGH,
                        new BigDecimal(
                                "0.87"
                        ),
                        "Important work is blocked.",
                        "GOOGLE_GEMINI",
                        "gemini-3.8-flash"
                );

        AiTriageResponse response =
                persistenceService
                        .persistRecommendation(
                                engineerId,
                                ticketId,
                                result,
                                "triage-v1"
                        );

        assertEquals(
                recommendationId,
                response.recommendationId()
        );

        assertEquals(
                ticketId,
                response.ticketId()
        );

        assertEquals(
                "FD-000033",
                response.ticketNumber()
        );

        assertEquals(
                TicketPriority.HIGH,
                response.suggestedPriority()
        );

        assertEquals(
                new BigDecimal(
                        "0.8700"
                ),
                response.confidence()
        );

        assertEquals(
                "Important work is blocked.",
                response.reason()
        );

        assertEquals(
                engineerId,
                response.generatedByUserId()
        );

        ArgumentCaptor<AiTriageRecommendation>
                recommendationCaptor =
                ArgumentCaptor.forClass(
                        AiTriageRecommendation.class
                );

        verify(
                recommendationRepository
        ).saveAndFlush(
                recommendationCaptor.capture()
        );

        AiTriageRecommendation saved =
                recommendationCaptor.getValue();

        assertEquals(
                TicketPriority.HIGH,
                saved.getSuggestedPriority()
        );

        assertEquals(
                new BigDecimal(
                        "0.8700"
                ),
                saved.getConfidence()
        );

        verify(
                auditService
        ).recordUserAction(
                eq(engineer),
                eq(
                        AuditAction
                                .AI_TRIAGE_GENERATED
                ),
                eq(
                        AuditTargetType.TICKET
                ),
                eq(ticketId),
                eq(
                        "FD-000033"
                ),
                eq(
                        Map.of(
                                "suggestedPriority",
                                "HIGH",

                                "confidence",
                                "0.8700",

                                "provider",
                                "GOOGLE_GEMINI",

                                "model",
                                "gemini-3.8-flash",

                                "promptVersion",
                                "triage-v1"
                        )
                )
        );
    }

    @Test
    void persistRecommendationRejectsDifferentEngineer() {

        UUID requestedEngineerId =
                UUID.randomUUID();

        UUID actualEngineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User actualEngineer =
                mock(User.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                actualEngineer
        );

        when(
                actualEngineer.getId()
        ).thenReturn(
                actualEngineerId
        );

        AiTriageClientResult result =
                createResult();

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                persistenceService
                                        .persistRecommendation(
                                                requestedEngineerId,
                                                ticketId,
                                                result,
                                                "triage-v1"
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                recommendationRepository,
                auditService
        );
    }

    @Test
    void persistRecommendationRejectsDuplicate() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User engineer =
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                engineer
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                recommendationRepository
                        .existsByTicket_Id(
                                ticketId
                        )
        ).thenReturn(
                true
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                persistenceService
                                        .persistRecommendation(
                                                engineerId,
                                                ticketId,
                                                createResult(),
                                                "triage-v1"
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                auditService
        );
    }

    @Test
    void databaseUniqueConstraintRaceReturnsConflict() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        User engineer =
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_IdAndReleasedAtIsNull(
                                ticketId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getAssignedToUser()
        ).thenReturn(
                engineer
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                recommendationRepository
                        .existsByTicket_Id(
                                ticketId
                        )
        ).thenReturn(
                false
        );

        when(
                recommendationRepository
                        .saveAndFlush(
                                any(
                                        AiTriageRecommendation.class
                                )
                        )
        ).thenThrow(
                new DataIntegrityViolationException(
                        "duplicate ticket"
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                persistenceService
                                        .persistRecommendation(
                                                engineerId,
                                                ticketId,
                                                createResult(),
                                                "triage-v1"
                                        )
                );

        assertEquals(
                HttpStatus.CONFLICT,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                auditService
        );
    }

    @Test
    void getRecommendationReturnsStoredRecommendation() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        UUID recommendationId =
                UUID.randomUUID();

        Ticket ticket =
                mock(Ticket.class);

        User engineer =
                mock(User.class);

        TicketAssignment assignment =
                mock(TicketAssignment.class);

        when(
                ticketAssignmentRepository
                        .findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                                "FD-000034",
                                engineerId
                        )
        ).thenReturn(
                Optional.of(
                        assignment
                )
        );

        when(
                assignment.getTicket()
        ).thenReturn(
                ticket
        );

        when(
                ticket.getId()
        ).thenReturn(
                ticketId
        );

        when(
                ticket.getTicketNumber()
        ).thenReturn(
                "FD-000034"
        );

        when(
                engineer.getId()
        ).thenReturn(
                engineerId
        );

        AiTriageRecommendation recommendation =
                new AiTriageRecommendation(
                        ticket,
                        engineer,
                        TicketPriority.MEDIUM,
                        new BigDecimal(
                                "0.81"
                        ),
                        "Normal user impact.",
                        "GOOGLE_GEMINI",
                        "gemini-3.8-flash",
                        "triage-v1",
                        java.time.OffsetDateTime.now(
                                java.time.ZoneOffset.UTC
                        )
                );

        ReflectionTestUtils.setField(
                recommendation,
                "id",
                recommendationId
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

        AiTriageResponse response =
                persistenceService
                        .getRecommendation(
                                engineerId,
                                "fd-000034"
                        );

        assertEquals(
                recommendationId,
                response.recommendationId()
        );

        assertEquals(
                TicketPriority.MEDIUM,
                response.suggestedPriority()
        );

        assertEquals(
                new BigDecimal(
                        "0.8100"
                ),
                response.confidence()
        );
    }

    private AiTriageClientResult createResult() {

        return new AiTriageClientResult(
                TicketPriority.HIGH,
                new BigDecimal(
                        "0.87"
                ),
                "Important work is blocked.",
                "GOOGLE_GEMINI",
                "gemini-3.8-flash"
        );
    }
}