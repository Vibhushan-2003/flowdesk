package com.flowdesk.ai.client;

public class AiResolutionSummaryClientException
        extends RuntimeException {

    public AiResolutionSummaryClientException(
            String message
    ) {
        super(
                message
        );
    }

    public AiResolutionSummaryClientException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}