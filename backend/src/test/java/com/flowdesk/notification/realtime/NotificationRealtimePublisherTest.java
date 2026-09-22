package com.flowdesk.notification.realtime;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.dto.NotificationResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void shouldSendNotificationToCorrectUserDestination() {

        UUID recipientUserId =
                UUID.randomUUID();

        NotificationResponse notification =
                createNotificationResponse();

        NotificationCreatedEvent event =
                new NotificationCreatedEvent(
                        recipientUserId,
                        notification
                );

        NotificationRealtimePublisher publisher =
                new NotificationRealtimePublisher(
                        messagingTemplate
                );

        publisher.handleNotificationCreated(
                event
        );

        verify(messagingTemplate)
                .convertAndSendToUser(
                        recipientUserId.toString(),
                        "/queue/notifications",
                        notification
                );
    }

    @Test
    void websocketFailureShouldNotThrowAfterDatabaseCommit() {

        UUID recipientUserId =
                UUID.randomUUID();

        NotificationResponse notification =
                createNotificationResponse();

        NotificationCreatedEvent event =
                new NotificationCreatedEvent(
                        recipientUserId,
                        notification
                );

        doThrow(
                new RuntimeException(
                        "WebSocket unavailable"
                )
        )
                .when(messagingTemplate)
                .convertAndSendToUser(
                        recipientUserId.toString(),
                        "/queue/notifications",
                        notification
                );

        NotificationRealtimePublisher publisher =
                new NotificationRealtimePublisher(
                        messagingTemplate
                );

        assertDoesNotThrow(
                () ->
                        publisher
                                .handleNotificationCreated(
                                        event
                                )
        );
    }

    private NotificationResponse
            createNotificationResponse() {

        return new NotificationResponse(
                UUID.randomUUID(),
                NotificationType
                        .TICKET_STATUS_CHANGED,
                "Ticket updated",
                "Your ticket status changed",
                UUID.randomUUID(),
                "Support Engineer",
                UUID.randomUUID(),
                "FD-000010",
                false,
                null,
                OffsetDateTime.now()
        );
    }
}