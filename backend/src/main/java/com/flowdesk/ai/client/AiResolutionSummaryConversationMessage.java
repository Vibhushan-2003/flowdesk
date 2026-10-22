package com.flowdesk.ai.client;

import java.util.Objects;

public record AiResolutionSummaryConversationMessage(

        AiResolutionSummaryMessageRole role,
        String body

) {

    public AiResolutionSummaryConversationMessage {

        Objects.requireNonNull(
                role,
                "Conversation message role is required"
        );

        if (body == null
                || body.isBlank()) {

            throw new IllegalArgumentException(
                    "Conversation message body is required"
            );
        }

        body =
                body.trim();
    }
}