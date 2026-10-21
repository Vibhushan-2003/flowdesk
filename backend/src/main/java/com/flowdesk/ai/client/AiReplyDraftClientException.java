package com.flowdesk.ai.client;

public class AiReplyDraftClientException
        extends RuntimeException {

    public AiReplyDraftClientException(
            String message
    ) {
        super(message);
    }

    public AiReplyDraftClientException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}