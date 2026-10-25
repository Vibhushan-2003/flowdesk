package com.flowdesk.email.model;

import java.time.Instant;

/**
 * Result record of an outbound email delivery attempt.
 *
 * @param messageId Unique tracking ID of the message.
 * @param recipient Email address of the recipient.
 * @param status Delivery outcome status.
 * @param sentAt Timestamp of the attempt.
 * @param details High-level summary of the attempt outcome (never contains raw email body or secrets).
 */
public record EmailDeliveryResult(
        String messageId,
        String recipient,
        EmailDeliveryStatus status,
        Instant sentAt,
        String details
) {
    public static EmailDeliveryResult accepted(String messageId, String recipient) {
        return new EmailDeliveryResult(messageId, recipient, EmailDeliveryStatus.ACCEPTED, Instant.now(), "Accepted for delivery by SMTP transport");
    }

    public static EmailDeliveryResult disabled(String recipient) {
        return new EmailDeliveryResult(null, recipient, EmailDeliveryStatus.DISABLED, Instant.now(), "Email delivery is disabled by configuration");
    }

    public static EmailDeliveryResult failed(String messageId, String recipient, String failureSummary) {
        return new EmailDeliveryResult(messageId, recipient, EmailDeliveryStatus.FAILED, Instant.now(), failureSummary);
    }
}
