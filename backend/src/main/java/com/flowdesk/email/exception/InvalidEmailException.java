package com.flowdesk.email.exception;

/**
 * Thrown when an outgoing email message fails validation (e.g. invalid recipient,
 * empty body, or header injection attempt).
 */
public class InvalidEmailException extends EmailDeliveryException {

    public InvalidEmailException(String message) {
        super(message);
    }
}
