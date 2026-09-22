package com.flowdesk.notification.service;

import com.flowdesk.common.dto.PageResponse;

import com.flowdesk.notification.domain.Notification;
import com.flowdesk.notification.domain.NotificationType;

import com.flowdesk.notification.dto.NotificationResponse;
import com.flowdesk.notification.dto.UnreadNotificationCountResponse;

import com.flowdesk.notification.realtime.NotificationCreatedEvent;

import com.flowdesk.notification.repository.NotificationRepository;

import com.flowdesk.ticket.domain.Ticket;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository
            notificationRepository;

    @Mock
    private ApplicationEventPublisher
            applicationEventPublisher;

    private NotificationService
            notificationService;

    private UUID recipientUserId;

    @BeforeEach
    void setUp() {

        notificationService =
                new NotificationService(
                        notificationRepository,
                        applicationEventPublisher
                );

        recipientUserId =
                UUID.randomUUID();
    }

    @Test
    void createNotificationShouldNormalizeAndPersistNotification() {

        User recipientUser =
                mock(User.class);

        User actorUser =
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        UUID actorUserId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        when(recipientUser.getId())
                .thenReturn(
                        recipientUserId
                );

        when(actorUser.getId())
                .thenReturn(
                        actorUserId
                );

        when(actorUser.getFirstName())
                .thenReturn(
                        "Auth"
                );

        when(actorUser.getLastName())
                .thenReturn(
                        "Tester"
                );

        when(ticket.getId())
                .thenReturn(
                        ticketId
                );

        when(ticket.getTicketNumber())
                .thenReturn(
                        "FD-000008"
                );

        when(
                notificationRepository
                        .saveAndFlush(
                                any(Notification.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        NotificationResponse response =
                notificationService
                        .createNotification(
                                recipientUser,
                                actorUser,
                                ticket,
                                NotificationType
                                        .TICKET_COMMENT_ADDED,
                                "  New ticket reply  ",
                                "  Auth Tester replied to FD-000008  "
                        );

        assertEquals(
                NotificationType.TICKET_COMMENT_ADDED,
                response.type()
        );

        assertEquals(
                "New ticket reply",
                response.title()
        );

        assertEquals(
                "Auth Tester replied to FD-000008",
                response.message()
        );

        assertEquals(
                actorUserId,
                response.actorUserId()
        );

        assertEquals(
                "Auth Tester",
                response.actorName()
        );

        assertEquals(
                ticketId,
                response.ticketId()
        );

        assertEquals(
                "FD-000008",
                response.ticketNumber()
        );

        assertFalse(
                response.read()
        );

        ArgumentCaptor<Notification>
                notificationCaptor =
                ArgumentCaptor.forClass(
                        Notification.class
                );

        verify(notificationRepository)
                .saveAndFlush(
                        notificationCaptor.capture()
                );

        Notification savedNotification =
                notificationCaptor.getValue();

        assertSame(
                recipientUser,
                savedNotification
                        .getRecipientUser()
        );

        assertSame(
                actorUser,
                savedNotification
                        .getActorUser()
        );

        assertSame(
                ticket,
                savedNotification
                        .getTicket()
        );

        assertEquals(
                NotificationType.TICKET_COMMENT_ADDED,
                savedNotification.getType()
        );

        assertEquals(
                "New ticket reply",
                savedNotification.getTitle()
        );

        assertEquals(
                "Auth Tester replied to FD-000008",
                savedNotification.getMessage()
        );
    }

    @Test
    void createNotificationShouldPublishCreatedEvent() {

        User recipientUser =
                mock(User.class);

        UUID userId =
                UUID.randomUUID();

        when(recipientUser.getId())
                .thenReturn(
                        userId
                );

        when(
                notificationRepository
                        .saveAndFlush(
                                any(Notification.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        NotificationResponse response =
                notificationService
                        .createNotification(
                                recipientUser,
                                null,
                                null,
                                NotificationType
                                        .TICKET_STATUS_CHANGED,
                                "Ticket updated",
                                "Your ticket status changed"
                        );

        ArgumentCaptor<NotificationCreatedEvent>
                eventCaptor =
                ArgumentCaptor.forClass(
                        NotificationCreatedEvent.class
                );

        verify(applicationEventPublisher)
                .publishEvent(
                        eventCaptor.capture()
                );

        NotificationCreatedEvent event =
                eventCaptor.getValue();

        assertEquals(
                userId,
                event.recipientUserId()
        );

        assertSame(
                response,
                event.notification()
        );

        assertEquals(
                NotificationType.TICKET_STATUS_CHANGED,
                event.notification().type()
        );

        assertEquals(
                "Ticket updated",
                event.notification().title()
        );

        assertEquals(
                "Your ticket status changed",
                event.notification().message()
        );
    }

    @Test
    void getMyNotificationsShouldReturnPaginatedNotifications() {

        Notification newestNotification =
                mock(Notification.class);

        Notification olderNotification =
                mock(Notification.class);

        User actorUser =
                mock(User.class);

        Ticket ticket =
                mock(Ticket.class);

        UUID newestNotificationId =
                UUID.randomUUID();

        UUID olderNotificationId =
                UUID.randomUUID();

        UUID actorUserId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        OffsetDateTime newestCreatedAt =
                OffsetDateTime.parse(
                        "2026-09-22T04:30:00Z"
                );

        OffsetDateTime olderCreatedAt =
                OffsetDateTime.parse(
                        "2026-09-22T04:20:00Z"
                );

        when(newestNotification.getId())
                .thenReturn(
                        newestNotificationId
                );

        when(newestNotification.getType())
                .thenReturn(
                        NotificationType
                                .TICKET_COMMENT_ADDED
                );

        when(newestNotification.getTitle())
                .thenReturn(
                        "New ticket reply"
                );

        when(newestNotification.getMessage())
                .thenReturn(
                        "Auth Tester replied to FD-000008"
                );

        when(newestNotification.getActorUser())
                .thenReturn(
                        actorUser
                );

        when(newestNotification.getTicket())
                .thenReturn(
                        ticket
                );

        when(newestNotification.isRead())
                .thenReturn(
                        false
                );

        when(newestNotification.getReadAt())
                .thenReturn(
                        null
                );

        when(newestNotification.getCreatedAt())
                .thenReturn(
                        newestCreatedAt
                );

        when(actorUser.getId())
                .thenReturn(
                        actorUserId
                );

        when(actorUser.getFirstName())
                .thenReturn(
                        "Auth"
                );

        when(actorUser.getLastName())
                .thenReturn(
                        "Tester"
                );

        when(ticket.getId())
                .thenReturn(
                        ticketId
                );

        when(ticket.getTicketNumber())
                .thenReturn(
                        "FD-000008"
                );

        when(olderNotification.getId())
                .thenReturn(
                        olderNotificationId
                );

        when(olderNotification.getType())
                .thenReturn(
                        NotificationType
                                .TICKET_STATUS_CHANGED
                );

        when(olderNotification.getTitle())
                .thenReturn(
                        "Ticket status updated"
                );

        when(olderNotification.getMessage())
                .thenReturn(
                        "FD-000008 is now IN_PROGRESS"
                );

        when(olderNotification.getActorUser())
                .thenReturn(
                        null
                );

        when(olderNotification.getTicket())
                .thenReturn(
                        null
                );

        when(olderNotification.isRead())
                .thenReturn(
                        true
                );

        OffsetDateTime readAt =
                OffsetDateTime.parse(
                        "2026-09-22T04:25:00Z"
                );

        when(olderNotification.getReadAt())
                .thenReturn(
                        readAt
                );

        when(olderNotification.getCreatedAt())
                .thenReturn(
                        olderCreatedAt
                );

        PageRequest pageRequest =
                PageRequest.of(
                        0,
                        20
                );

        when(
                notificationRepository
                        .findByRecipientUser_IdOrderByCreatedAtDesc(
                                eq(recipientUserId),
                                any(Pageable.class)
                        )
        ).thenReturn(
                new PageImpl<>(
                        List.of(
                                newestNotification,
                                olderNotification
                        ),
                        pageRequest,
                        2
                )
        );

        PageResponse<NotificationResponse> response =
                notificationService
                        .getMyNotifications(
                                recipientUserId,
                                0,
                                20
                        );

        assertEquals(
                2,
                response.content().size()
        );

        assertEquals(
                newestNotificationId,
                response.content()
                        .get(0)
                        .notificationId()
        );

        assertEquals(
                "New ticket reply",
                response.content()
                        .get(0)
                        .title()
        );

        assertEquals(
                "Auth Tester",
                response.content()
                        .get(0)
                        .actorName()
        );

        assertEquals(
                "FD-000008",
                response.content()
                        .get(0)
                        .ticketNumber()
        );

        assertFalse(
                response.content()
                        .get(0)
                        .read()
        );

        assertEquals(
                olderNotificationId,
                response.content()
                        .get(1)
                        .notificationId()
        );

        assertTrue(
                response.content()
                        .get(1)
                        .read()
        );

        assertEquals(
                0,
                response.page()
        );

        assertEquals(
                20,
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

        assertTrue(
                response.first()
        );

        assertTrue(
                response.last()
        );

        ArgumentCaptor<Pageable>
                pageableCaptor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(notificationRepository)
                .findByRecipientUser_IdOrderByCreatedAtDesc(
                        eq(recipientUserId),
                        pageableCaptor.capture()
                );

        Pageable capturedPageable =
                pageableCaptor.getValue();

        assertEquals(
                0,
                capturedPageable
                        .getPageNumber()
        );

        assertEquals(
                20,
                capturedPageable
                        .getPageSize()
        );
    }

    @Test
    void getUnreadCountShouldReturnRepositoryCount() {

        when(
                notificationRepository
                        .countByRecipientUser_IdAndReadAtIsNull(
                                recipientUserId
                        )
        ).thenReturn(
                4L
        );

        UnreadNotificationCountResponse response =
                notificationService
                        .getUnreadCount(
                                recipientUserId
                        );

        assertEquals(
                4L,
                response.unreadCount()
        );

        verify(notificationRepository)
                .countByRecipientUser_IdAndReadAtIsNull(
                        recipientUserId
                );
    }

    @Test
    void markAsReadShouldMarkOwnedNotificationAsRead() {

        UUID notificationId =
                UUID.randomUUID();

        User recipientUser =
                mock(User.class);

        Notification notification =
                new Notification(
                        recipientUser,
                        null,
                        null,
                        NotificationType
                                .TICKET_STATUS_CHANGED,
                        "Ticket status updated",
                        "FD-000008 is now IN_PROGRESS"
                );

        when(
                notificationRepository
                        .findByIdAndRecipientUser_Id(
                                notificationId,
                                recipientUserId
                        )
        ).thenReturn(
                Optional.of(
                        notification
                )
        );

        assertFalse(
                notification.isRead()
        );

        NotificationResponse response =
                notificationService
                        .markAsRead(
                                recipientUserId,
                                notificationId
                        );

        assertTrue(
                notification.isRead()
        );

        assertTrue(
                response.read()
        );

        assertNotNull(
                response.readAt()
        );

        verify(notificationRepository)
                .findByIdAndRecipientUser_Id(
                        notificationId,
                        recipientUserId
                );

        verify(
                notificationRepository,
                never()
        ).save(
                any(Notification.class)
        );
    }

    @Test
    void markAsReadShouldBeIdempotent() {

        UUID notificationId =
                UUID.randomUUID();

        User recipientUser =
                mock(User.class);

        Notification notification =
                new Notification(
                        recipientUser,
                        null,
                        null,
                        NotificationType
                                .TICKET_RESOLVED,
                        "Ticket resolved",
                        "FD-000008 has been resolved"
                );

        notification.markAsRead();

        OffsetDateTime originalReadAt =
                notification.getReadAt();

        when(
                notificationRepository
                        .findByIdAndRecipientUser_Id(
                                notificationId,
                                recipientUserId
                        )
        ).thenReturn(
                Optional.of(
                        notification
                )
        );

        NotificationResponse response =
                notificationService
                        .markAsRead(
                                recipientUserId,
                                notificationId
                        );

        assertEquals(
                originalReadAt,
                notification.getReadAt()
        );

        assertEquals(
                originalReadAt,
                response.readAt()
        );

        assertTrue(
                response.read()
        );
    }

    @Test
    void markAsReadShouldReturnNotFoundWhenNotificationIsNotOwned() {

        UUID notificationId =
                UUID.randomUUID();

        when(
                notificationRepository
                        .findByIdAndRecipientUser_Id(
                                notificationId,
                                recipientUserId
                        )
        ).thenReturn(
                Optional.empty()
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                notificationService
                                        .markAsRead(
                                                recipientUserId,
                                                notificationId
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verify(notificationRepository)
                .findByIdAndRecipientUser_Id(
                        notificationId,
                        recipientUserId
                );
    }

    @Test
    void getMyNotificationsShouldRejectNegativePage() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                notificationService
                                        .getMyNotifications(
                                                recipientUserId,
                                                -1,
                                                20
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }

    @Test
    void getMyNotificationsShouldRejectInvalidPageSize() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                notificationService
                                        .getMyNotifications(
                                                recipientUserId,
                                                0,
                                                101
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );
    }
}