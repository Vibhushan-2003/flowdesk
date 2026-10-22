package com.flowdesk.ai.client;

public class AiSupportCopilotClientException
        extends RuntimeException {

    public AiSupportCopilotClientException(
            String message
    ) {
        super(message);
    }

    public AiSupportCopilotClientException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}