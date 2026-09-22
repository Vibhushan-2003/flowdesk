package com.flowdesk.notification.realtime;

import com.flowdesk.notification.dto.NotificationResponse;

import java.util.UUID;

public record NotificationCreatedEvent(
        UUID recipientUserId,
        NotificationResponse notification
) {
}