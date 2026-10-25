package com.flowdesk.email.model;

/**
 * Status of an outbound email delivery attempt.
 */
public enum EmailDeliveryStatus {
    /**
     * Accepted by the SMTP transport for delivery.
     */
    ACCEPTED,

    /**
     * Delivery failed due to transport, configuration, or network error.
     */
    FAILED,

    /**
     * Email delivery is disabled in current environment (e.g. local test or disabled flag).
     */
    DISABLED
}
