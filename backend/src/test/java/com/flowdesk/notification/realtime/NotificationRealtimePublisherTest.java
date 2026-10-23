
package com.flowdesk.notification.realtime;

import com.flowdesk.notification.domain.NotificationType;
import com.flowdesk.notification.dto.NotificationResponse;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    /*
     * TEST 1
     *
     * An ACTIVE user should still receive
     * their real-time notification.
     */
    @Test
    void shouldSendNotificationToCorrectUserDestination() {

        UUID recipientUserId = UUID.randomUUID();

        NotificationResponse notification =
                createNotificationResponse();

        User user = mock(User.class);

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(userRepository.findById(recipientUserId))
                .thenReturn(Optional.of(user));

        NotificationCreatedEvent event =
                new NotificationCreatedEvent(
                        recipientUserId,
                        notification
                );

        NotificationRealtimePublisher publisher =
                new NotificationRealtimePublisher(
                        messagingTemplate,
                        userRepository
                );

        publisher.handleNotificationCreated(event);

        verify(messagingTemplate)
                .convertAndSendToUser(
                        recipientUserId.toString(),
                        "/queue/notifications",
                        notification
                );
    }

    /*
     * TEST 2
     *
     * WebSocket delivery failure must not
     * break the already-committed transaction.
     */
    @Test
    void websocketFailureShouldNotThrowAfterDatabaseCommit() {

        UUID recipientUserId = UUID.randomUUID();

        NotificationResponse notification =
                createNotificationResponse();

        User user = mock(User.class);

        when(user.getStatus())
                .thenReturn(UserStatus.ACTIVE);

        when(userRepository.findById(recipientUserId))
                .thenReturn(Optional.of(user));

        NotificationCreatedEvent event =
                new NotificationCreatedEvent(
                        recipientUserId,
                        notification
                );

        doThrow(
                new RuntimeException("WebSocket unavailable")
        )
                .when(messagingTemplate)
                .convertAndSendToUser(
                        recipientUserId.toString(),
                        "/queue/notifications",
                        notification
                );

        NotificationRealtimePublisher publisher =
                new NotificationRealtimePublisher(
                        messagingTemplate,
                        userRepository
                );

        assertDoesNotThrow(
                () -> publisher.handleNotificationCreated(event)
        );
    }

    /*
     * TEST 3 - DAY 37 PHASE 4
     *
     * A user may have subscribed while ACTIVE,
     * then become INACTIVE.
     *
     * The notification publisher must not send
     * new notifications to that inactive account.
     */
    @Test
    void inactiveRecipientMustNotReceiveRealtimeNotification() {

        UUID recipientUserId = UUID.randomUUID();

        NotificationResponse notification =
                createNotificationResponse();

        User user = mock(User.class);

        when(user.getStatus())
                .thenReturn(UserStatus.INACTIVE);

        when(userRepository.findById(recipientUserId))
                .thenReturn(Optional.of(user));

        NotificationCreatedEvent event =
                new NotificationCreatedEvent(
                        recipientUserId,
                        notification
                );

        NotificationRealtimePublisher publisher =
                new NotificationRealtimePublisher(
                        messagingTemplate,
                        userRepository
                );

        publisher.handleNotificationCreated(event);

        verify(messagingTemplate, never())
                .convertAndSendToUser(
                        recipientUserId.toString(),
                        "/queue/notifications",
                        notification
                );
    }

    private NotificationResponse createNotificationResponse() {

        return new NotificationResponse(
                UUID.randomUUID(),
                NotificationType.TICKET_STATUS_CHANGED,
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

/*
 * TEST 4
 *
 * If the recipient no longer exists,
 * no WebSocket notification should be sent.
 */
@Test
void missingRecipientMustNotReceiveRealtimeNotification() {

    UUID recipientUserId = UUID.randomUUID();

    NotificationResponse notification =
            createNotificationResponse();

    when(userRepository.findById(recipientUserId))
            .thenReturn(Optional.empty());

    NotificationCreatedEvent event =
            new NotificationCreatedEvent(
                    recipientUserId,
                    notification
            );

    NotificationRealtimePublisher publisher =
            new NotificationRealtimePublisher(
                    messagingTemplate,
                    userRepository
            );

    publisher.handleNotificationCreated(event);

    verify(messagingTemplate, never())
            .convertAndSendToUser(
                    any(String.class),
                    any(String.class),
                    any()
            );
}

/*
 * TEST 5
 *
 * If the database cannot verify the recipient,
 * delivery should fail closed without throwing.
 */
@Test
void databaseLookupFailureMustNotDeliverNotification() {

    UUID recipientUserId = UUID.randomUUID();

    NotificationResponse notification =
            createNotificationResponse();

    when(userRepository.findById(recipientUserId))
            .thenThrow(
                    new RuntimeException(
                            "Database temporarily unavailable"
                    )
            );

    NotificationCreatedEvent event =
            new NotificationCreatedEvent(
                    recipientUserId,
                    notification
            );

    NotificationRealtimePublisher publisher =
            new NotificationRealtimePublisher(
                    messagingTemplate,
                    userRepository
            );

    assertDoesNotThrow(
            () -> publisher.handleNotificationCreated(event)
    );

    verify(messagingTemplate, never())
            .convertAndSendToUser(
                    any(String.class),
                    any(String.class),
                    any()
            );
}

}
