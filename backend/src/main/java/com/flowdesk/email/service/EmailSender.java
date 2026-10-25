package com.flowdesk.email.service;

import com.flowdesk.email.exception.EmailDeliveryException;
import com.flowdesk.email.model.EmailDeliveryResult;
import com.flowdesk.email.model.EmailMessage;

/**
 * Core outbound email delivery service abstraction for FlowDesk.
 * <p>
 * Decoupled from user management, authentication, ticket workflow, and notification logic.
 */
public interface EmailSender {

    /**
     * Sends an email message via the configured outbound transport.
     *
     * @param message Validated outbound email message.
     * @return Result of the delivery attempt.
     * @throws EmailDeliveryException if transport or message construction fails.
     */
    EmailDeliveryResult send(EmailMessage message) throws EmailDeliveryException;
}
