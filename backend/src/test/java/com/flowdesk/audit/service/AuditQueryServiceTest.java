package com.flowdesk.audit.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditActorType;
import com.flowdesk.audit.domain.AuditLog;
import com.flowdesk.audit.domain.AuditTargetType;

import com.flowdesk.audit.dto.AuditEventResponse;
import com.flowdesk.audit.repository.AuditLogRepository;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryServiceTest {

    @Mock
    private AuditLogRepository
            auditLogRepository;

    private AuditQueryService
            auditQueryService;

    @BeforeEach
    void setUp() {
        auditQueryService =
                new AuditQueryService(
                        auditLogRepository
                );
    }

    @Test
    void getAuditEventsReturnsNewestEventsAndMapsUserAndSystemActors() {

        UUID userAuditId =
                UUID.randomUUID();

        UUID systemAuditId =
                UUID.randomUUID();

        UUID actorUserId =
                UUID.randomUUID();

        UUID ticketIdOne =
                UUID.randomUUID();

        UUID ticketIdTwo =
                UUID.randomUUID();

        OffsetDateTime newestOccurredAt =
                OffsetDateTime.parse(
                        "2026-09-25T10:30:00Z"
                );

        OffsetDateTime olderOccurredAt =
                OffsetDateTime.parse(
                        "2026-09-25T10:00:00Z"
                );

        User actorUser =
                mock(User.class);

        AuditLog userAudit =
                mock(AuditLog.class);

        AuditLog systemAudit =
                mock(AuditLog.class);

        when(
                actorUser.getId()
        ).thenReturn(
                actorUserId
        );

        when(
                userAudit.getId()
        ).thenReturn(
                userAuditId
        );

        when(
                userAudit.getActorType()
        ).thenReturn(
                AuditActorType.USER
        );

        when(
                userAudit.getActorUser()
        ).thenReturn(
                actorUser
        );

        when(
                userAudit.getActorEmail()
        ).thenReturn(
                "engineer@example.com"
        );

        when(
                userAudit.getAction()
        ).thenReturn(
                AuditAction.TICKET_STATUS_CHANGED
        );

        when(
                userAudit.getTargetType()
        ).thenReturn(
                AuditTargetType.TICKET
        );

        when(
                userAudit.getTargetId()
        ).thenReturn(
                ticketIdOne
        );

        when(
                userAudit.getTargetReference()
        ).thenReturn(
                "FD-000010"
        );

        when(
                userAudit.getMetadata()
        ).thenReturn(
                Map.of(
                        "fromStatus",
                        "ASSIGNED",
                        "toStatus",
                        "IN_PROGRESS"
                )
        );

        when(
                userAudit.getOccurredAt()
        ).thenReturn(
                newestOccurredAt
        );

        when(
                systemAudit.getId()
        ).thenReturn(
                systemAuditId
        );

        when(
                systemAudit.getActorType()
        ).thenReturn(
                AuditActorType.SYSTEM
        );

        when(
                systemAudit.getActorUser()
        ).thenReturn(
                null
        );

        when(
                systemAudit.getActorEmail()
        ).thenReturn(
                null
        );

        when(
                systemAudit.getAction()
        ).thenReturn(
                AuditAction.SLA_RESPONSE_BREACHED
        );

        when(
                systemAudit.getTargetType()
        ).thenReturn(
                AuditTargetType.TICKET
        );

        when(
                systemAudit.getTargetId()
        ).thenReturn(
                ticketIdTwo
        );

        when(
                systemAudit.getTargetReference()
        ).thenReturn(
                "FD-000011"
        );

        when(
                systemAudit.getMetadata()
        ).thenReturn(
                Map.of(
                        "deadline",
                        "2026-09-25T09:45Z",
                        "eventType",
                        "RESPONSE_BREACHED"
                )
        );

        when(
                systemAudit.getOccurredAt()
        ).thenReturn(
                olderOccurredAt
        );

        when(
                auditLogRepository
                        .findAllByOrderByOccurredAtDesc(
                                any(Pageable.class)
                        )
        ).thenReturn(
                new PageImpl<>(
                        List.of(
                                userAudit,
                                systemAudit
                        ),
                        PageRequest.of(
                                0,
                                50
                        ),
                        2
                )
        );

        PageResponse<AuditEventResponse> response =
                auditQueryService
                        .getAuditEvents(
                                0,
                                50
                        );

        assertEquals(
                2,
                response.content()
                        .size()
        );

        assertEquals(
                0,
                response.page()
        );

        assertEquals(
                50,
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

        AuditEventResponse firstEvent =
                response.content()
                        .get(0);

        assertEquals(
                userAuditId,
                firstEvent.id()
        );

        assertEquals(
                AuditActorType.USER,
                firstEvent.actorType()
        );

        assertEquals(
                actorUserId,
                firstEvent.actorUserId()
        );

        assertEquals(
                "engineer@example.com",
                firstEvent.actorEmail()
        );

        assertEquals(
                AuditAction.TICKET_STATUS_CHANGED,
                firstEvent.action()
        );

        assertEquals(
                AuditTargetType.TICKET,
                firstEvent.targetType()
        );

        assertEquals(
                ticketIdOne,
                firstEvent.targetId()
        );

        assertEquals(
                "FD-000010",
                firstEvent.targetReference()
        );

        assertEquals(
                "ASSIGNED",
                firstEvent.metadata()
                        .get(
                                "fromStatus"
                        )
        );

        assertEquals(
                "IN_PROGRESS",
                firstEvent.metadata()
                        .get(
                                "toStatus"
                        )
        );

        assertEquals(
                newestOccurredAt,
                firstEvent.occurredAt()
        );

        AuditEventResponse secondEvent =
                response.content()
                        .get(1);

        assertEquals(
                systemAuditId,
                secondEvent.id()
        );

        assertEquals(
                AuditActorType.SYSTEM,
                secondEvent.actorType()
        );

        assertNull(
                secondEvent.actorUserId()
        );

        assertNull(
                secondEvent.actorEmail()
        );

        assertEquals(
                AuditAction.SLA_RESPONSE_BREACHED,
                secondEvent.action()
        );

        assertEquals(
                "FD-000011",
                secondEvent.targetReference()
        );

        assertEquals(
                olderOccurredAt,
                secondEvent.occurredAt()
        );

        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(
                auditLogRepository
        ).findAllByOrderByOccurredAtDesc(
                pageableCaptor.capture()
        );

        Pageable pageable =
                pageableCaptor.getValue();

        assertEquals(
                0,
                pageable.getPageNumber()
        );

        assertEquals(
                50,
                pageable.getPageSize()
        );
    }

    @Test
    void getAuditEventsRejectsNegativePage() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                auditQueryService
                                        .getAuditEvents(
                                                -1,
                                                50
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "400 BAD_REQUEST \"Page must be zero or greater\"",
                exception.getMessage()
        );

        verify(
                auditLogRepository,
                never()
        ).findAllByOrderByOccurredAtDesc(
                any(Pageable.class)
        );
    }

    @Test
    void getAuditEventsRejectsZeroSize() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                auditQueryService
                                        .getAuditEvents(
                                                0,
                                                0
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "400 BAD_REQUEST \"Size must be between 1 and 100\"",
                exception.getMessage()
        );

        verify(
                auditLogRepository,
                never()
        ).findAllByOrderByOccurredAtDesc(
                any(Pageable.class)
        );
    }

    @Test
    void getAuditEventsRejectsSizeAboveMaximum() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                auditQueryService
                                        .getAuditEvents(
                                                0,
                                                101
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verify(
                auditLogRepository,
                never()
        ).findAllByOrderByOccurredAtDesc(
                any(Pageable.class)
        );
    }
}