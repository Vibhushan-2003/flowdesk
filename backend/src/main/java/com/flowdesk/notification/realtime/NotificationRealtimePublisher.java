
package com.flowdesk.notification.realtime;

import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class NotificationRealtimePublisher {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    NotificationRealtimePublisher.class
            );

    private static final String NOTIFICATION_DESTINATION =
            "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    private final UserRepository userRepository;

    public NotificationRealtimePublisher(
            SimpMessagingTemplate messagingTemplate,
            UserRepository userRepository
    ) {

        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    /*
     * DAY 37 - PHASE 4
     *
     * Database notifications remain the source
     * of truth.
     *
     * WebSocket delivery takes place only after
     * the database transaction commits.
     *
     * Before delivering a real-time notification,
     * check the recipient's current account status.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleNotificationCreated(
            NotificationCreatedEvent event
    ) {

        try {

            /*
             * Step 1:
             *
             * Check that the recipient still exists
             * and their account is currently ACTIVE.
             *
             * Missing or inactive accounts must
             * not receive real-time notifications.
             */
            boolean recipientIsActive =
                    userRepository
                            .findById(event.recipientUserId())
                            .map(user ->
                                    user.getStatus()
                                            == UserStatus.ACTIVE
                            )
                            .orElse(false);

            if (!recipientIsActive) {

                LOGGER.debug(
                        "Skipping real-time notification {} for unavailable recipient {}",
                        event.notification().notificationId(),
                        event.recipientUserId()
                );

                return;
            }

            /*
             * Step 2:
             *
             * The recipient is currently ACTIVE.
             *
             * Deliver the notification using
             * Spring's user-specific destination.
             */
            messagingTemplate.convertAndSendToUser(
                    event.recipientUserId().toString(),
                    NOTIFICATION_DESTINATION,
                    event.notification()
            );

        } catch (RuntimeException exception) {

            /*
             * Step 3:
             *
             * If account validation or WebSocket
             * delivery fails, do not send through
             * an alternative unchecked path.
             *
             * The database notification has already
             * been committed.
             *
             * The original REST operation must
             * not appear to have failed because
             * of real-time delivery.
             */
            LOGGER.warn(
                    "Failed to validate or deliver real-time notification {} to user {}",
                    event.notification().notificationId(),
                    event.recipientUserId(),
                    exception
            );
        }
    }
}
