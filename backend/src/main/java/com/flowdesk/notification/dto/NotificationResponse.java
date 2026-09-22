package com.flowdesk.notification.dto;

import com.flowdesk.notification.domain.NotificationType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        NotificationType type,
        String title,
        String message,

        UUID actorUserId,
        String actorName,

        UUID ticketId,
        String ticketNumber,

        boolean read,
        OffsetDateTime readAt,
        OffsetDateTime createdAt
) {
}