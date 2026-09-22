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

import org.springframework.context.ApplicationEventPublisher;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE =
            100;

    private final NotificationRepository
            notificationRepository;

    private final ApplicationEventPublisher
            applicationEventPublisher;

    public NotificationService(
            NotificationRepository notificationRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.notificationRepository =
                notificationRepository;

        this.applicationEventPublisher =
                applicationEventPublisher;
    }

    /**
     * Creates and persists a notification.
     *
     * recipientUser = user who should receive
     * the notification
     *
     * actorUser = user who caused the event,
     * nullable for system events
     *
     * ticket = related ticket,
     * nullable for non-ticket notifications
     */
    @Transactional
    public NotificationResponse createNotification(
            User recipientUser,
            User actorUser,
            Ticket ticket,
            NotificationType type,
            String title,
            String message
    ) {

        if (recipientUser == null) {
            throw new IllegalArgumentException(
                    "Notification recipient is required"
            );
        }

        if (recipientUser.getId() == null) {
            throw new IllegalArgumentException(
                    "Notification recipient must be persisted"
            );
        }

        Notification notification =
                new Notification(
                        recipientUser,
                        actorUser,
                        ticket,
                        type,
                        title,
                        message
                );

        Notification savedNotification =
                notificationRepository
                        .saveAndFlush(
                                notification
                        );

        NotificationResponse response =
                toResponse(
                        savedNotification
                );

        /*
         * Publish a Spring application event now.
         *
         * The WebSocket listener itself uses
         * AFTER_COMMIT, so no live message is sent
         * until the surrounding transaction commits.
         */
        applicationEventPublisher
                .publishEvent(
                        new NotificationCreatedEvent(
                                recipientUser.getId(),
                                response
                        )
                );

        return response;
    }

    /**
     * Returns only notifications belonging
     * to the logged-in user.
     */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse>
            getMyNotifications(
                    UUID recipientUserId,
                    int page,
                    int size
            ) {

        validateUserId(
                recipientUserId
        );

        validatePagination(
                page,
                size
        );

        PageRequest pageable =
                PageRequest.of(
                        page,
                        size
                );

        Page<Notification> notificationPage =
                notificationRepository
                        .findByRecipientUser_IdOrderByCreatedAtDesc(
                                recipientUserId,
                                pageable
                        );

        List<NotificationResponse> content =
                notificationPage
                        .getContent()
                        .stream()
                        .map(
                                this::toResponse
                        )
                        .toList();

        return new PageResponse<>(
                content,
                notificationPage.getNumber(),
                notificationPage.getSize(),
                notificationPage.getTotalElements(),
                notificationPage.getTotalPages(),
                notificationPage.isFirst(),
                notificationPage.isLast()
        );
    }

    /**
     * Returns the number of unread
     * notifications for one user.
     */
    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse
            getUnreadCount(
                    UUID recipientUserId
            ) {

        validateUserId(
                recipientUserId
        );

        long unreadCount =
                notificationRepository
                        .countByRecipientUser_IdAndReadAtIsNull(
                                recipientUserId
                        );

        return new UnreadNotificationCountResponse(
                unreadCount
        );
    }

    /**
     * Marks one notification as read.
     *
     * The repository query checks BOTH:
     *
     * notification ID + recipient user ID.
     *
     * Therefore one user cannot mark
     * another user's notification as read.
     */
    @Transactional
    public NotificationResponse markAsRead(
            UUID recipientUserId,
            UUID notificationId
    ) {

        validateUserId(
                recipientUserId
        );

        if (notificationId == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notification id is required"
            );
        }

        Notification notification =
                notificationRepository
                        .findByIdAndRecipientUser_Id(
                                notificationId,
                                recipientUserId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Notification not found"
                                        )
                        );

        notification.markAsRead();

        return toResponse(
                notification
        );
    }

    private NotificationResponse toResponse(
            Notification notification
    ) {

        User actorUser =
                notification.getActorUser();

        Ticket ticket =
                notification.getTicket();

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),

                actorUser != null
                        ? actorUser.getId()
                        : null,

                actorUser != null
                        ? buildUserDisplayName(
                                actorUser
                        )
                        : null,

                ticket != null
                        ? ticket.getId()
                        : null,

                ticket != null
                        ? ticket.getTicketNumber()
                        : null,

                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private String buildUserDisplayName(
            User user
    ) {

        String firstName =
                user.getFirstName() == null
                        ? ""
                        : user.getFirstName()
                                .trim();

        String lastName =
                user.getLastName() == null
                        ? ""
                        : user.getLastName()
                                .trim();

        String fullName =
                (
                        firstName
                                + " "
                                + lastName
                )
                        .trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        return user.getEmail();
    }

    private void validateUserId(
            UUID userId
    ) {

        if (userId == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User id is required"
            );
        }
    }

    private void validatePagination(
            int page,
            int size
    ) {

        if (page < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1
                || size > MAX_PAGE_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and "
                            + MAX_PAGE_SIZE
            );
        }
    }
}