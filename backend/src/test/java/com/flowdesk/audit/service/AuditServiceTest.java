package com.flowdesk.audit.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditActorType;
import com.flowdesk.audit.domain.AuditLog;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.repository.AuditLogRepository;
import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService =
                new AuditService(
                        auditLogRepository
                );
    }

    @Test
    void recordUserActionStoresActorSnapshotAndMetadata() {

        User actorUser =
                mock(User.class);

        UUID actorUserId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        OffsetDateTime occurredAt =
                OffsetDateTime.of(
                        2026,
                        9,
                        24,
                        12,
                        30,
                        0,
                        0,
                        ZoneOffset.UTC
                );

        when(actorUser.getId())
                .thenReturn(actorUserId);

        when(actorUser.getEmail())
                .thenReturn(
                        "engineer@example.com"
                );

        when(
                auditLogRepository
                        .saveAndFlush(
                                any(AuditLog.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        Map<String, Object> metadata =
                Map.of(
                        "from",
                        "ASSIGNED",
                        "to",
                        "IN_PROGRESS"
                );

        AuditLog result =
                auditService.recordUserAction(
                        actorUser,
                        AuditAction
                                .TICKET_STATUS_CHANGED,
                        AuditTargetType.TICKET,
                        ticketId,
                        "FD-000012",
                        metadata,
                        occurredAt
                );

        ArgumentCaptor<AuditLog> captor =
                ArgumentCaptor.forClass(
                        AuditLog.class
                );

        verify(auditLogRepository)
                .saveAndFlush(
                        captor.capture()
                );

        AuditLog saved =
                captor.getValue();

        assertSame(
                saved,
                result
        );

        assertEquals(
                AuditActorType.USER,
                saved.getActorType()
        );

        assertSame(
                actorUser,
                saved.getActorUser()
        );

        assertEquals(
                "engineer@example.com",
                saved.getActorEmail()
        );

        assertEquals(
                AuditAction.TICKET_STATUS_CHANGED,
                saved.getAction()
        );

        assertEquals(
                AuditTargetType.TICKET,
                saved.getTargetType()
        );

        assertEquals(
                ticketId,
                saved.getTargetId()
        );

        assertEquals(
                "FD-000012",
                saved.getTargetReference()
        );

        assertEquals(
                "ASSIGNED",
                saved.getMetadata().get("from")
        );

        assertEquals(
                "IN_PROGRESS",
                saved.getMetadata().get("to")
        );

        assertEquals(
                occurredAt,
                saved.getOccurredAt()
        );
    }

    @Test
    void recordSystemActionStoresNoUserIdentity() {

        UUID ticketId =
                UUID.randomUUID();

        OffsetDateTime occurredAt =
                OffsetDateTime.of(
                        2026,
                        9,
                        24,
                        13,
                        0,
                        0,
                        0,
                        ZoneOffset.UTC
                );

        when(
                auditLogRepository
                        .saveAndFlush(
                                any(AuditLog.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        AuditLog result =
                auditService.recordSystemAction(
                        AuditAction
                                .SLA_RESPONSE_BREACHED,
                        AuditTargetType.TICKET,
                        ticketId,
                        "FD-000011",
                        Map.of(
                                "deadline",
                                "2026-09-24T05:34:59Z"
                        ),
                        occurredAt
                );

        assertEquals(
                AuditActorType.SYSTEM,
                result.getActorType()
        );

        assertNull(
                result.getActorUser()
        );

        assertNull(
                result.getActorEmail()
        );

        assertEquals(
                AuditAction
                        .SLA_RESPONSE_BREACHED,
                result.getAction()
        );

        assertEquals(
                ticketId,
                result.getTargetId()
        );

        assertEquals(
                occurredAt,
                result.getOccurredAt()
        );
    }

    @Test
    void recordUserActionRejectsMissingActor() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        auditService
                                .recordUserAction(
                                        null,
                                        AuditAction
                                                .TICKET_CREATED,
                                        AuditTargetType
                                                .TICKET,
                                        UUID.randomUUID(),
                                        "FD-000012",
                                        Map.of(),
                                        OffsetDateTime.now(
                                                ZoneOffset.UTC
                                        )
                                )
        );
    }

    @Test
    void recordUserActionRejectsUnpersistedActor() {

        User actorUser =
                mock(User.class);

        when(actorUser.getId())
                .thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        auditService
                                .recordUserAction(
                                        actorUser,
                                        AuditAction
                                                .TICKET_CREATED,
                                        AuditTargetType
                                                .TICKET,
                                        UUID.randomUUID(),
                                        "FD-000012",
                                        Map.of(),
                                        OffsetDateTime.now(
                                                ZoneOffset.UTC
                                        )
                                )
        );
    }

    @Test
    void repositoryFailurePropagatesToCaller() {

        User actorUser =
                mock(User.class);

        when(actorUser.getId())
                .thenReturn(
                        UUID.randomUUID()
                );

        when(actorUser.getEmail())
                .thenReturn(
                        "employee@example.com"
                );

        when(
                auditLogRepository
                        .saveAndFlush(
                                any(AuditLog.class)
                        )
        ).thenThrow(
                new RuntimeException(
                        "Audit persistence failed"
                )
        );

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                auditService
                                        .recordUserAction(
                                                actorUser,
                                                AuditAction
                                                        .TICKET_CREATED,
                                                AuditTargetType
                                                        .TICKET,
                                                UUID.randomUUID(),
                                                "FD-000012",
                                                Map.of(),
                                                OffsetDateTime.now(
                                                        ZoneOffset.UTC
                                                )
                                        )
                );

        assertEquals(
                "Audit persistence failed",
                exception.getMessage()
        );
    }
}