package com.flowdesk.notification.realtime;

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

    public NotificationRealtimePublisher(
            SimpMessagingTemplate messagingTemplate
    ) {
        this.messagingTemplate =
                messagingTemplate;
    }

    /*
     * The database notification is the source of truth.
     *
     * WebSocket delivery happens only AFTER the
     * surrounding transaction commits successfully.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleNotificationCreated(
            NotificationCreatedEvent event
    ) {

        try {
            messagingTemplate.convertAndSendToUser(
                    event.recipientUserId()
                            .toString(),

                    NOTIFICATION_DESTINATION,

                    event.notification()
            );
        }
        catch (RuntimeException exception) {

            /*
             * WebSocket delivery is best-effort.
             *
             * The database transaction has already
             * committed at this point, so a temporary
             * socket problem must not make the REST
             * request appear to have failed.
             *
             * The user can still retrieve the
             * persisted notification later through
             * the normal REST API.
             */
            LOGGER.warn(
                    "Failed to deliver real-time notification {} to user {}",
                    event.notification()
                            .notificationId(),
                    event.recipientUserId(),
                    exception
            );
        }
    }
}