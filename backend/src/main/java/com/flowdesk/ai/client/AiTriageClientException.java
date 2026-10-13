package com.flowdesk.ai.client;

public class AiTriageClientException
        extends RuntimeException {

    public AiTriageClientException(
            String message
    ) {
        super(message);
    }

    public AiTriageClientException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}