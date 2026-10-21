package com.flowdesk.ai.client;

import java.util.Objects;

public record AiReplyDraftConversationMessage(

        AiReplyDraftMessageRole role,
        String body

) {

    private static final int MAX_BODY_LENGTH =
            4000;

    public AiReplyDraftConversationMessage {

        Objects.requireNonNull(
                role,
                "Conversation role is required"
        );

        body =
                normalizeRequiredText(
                        body,
                        "Conversation message is required"
                );

        if (body.length()
                > MAX_BODY_LENGTH) {

            throw new IllegalArgumentException(
                    "Conversation message must not exceed 4000 characters"
            );
        }
    }

    private static String normalizeRequiredText(
            String value,
            String requiredMessage
    ) {

        if (value == null) {
            throw new IllegalArgumentException(
                    requiredMessage
            );
        }

        String normalized =
                value.trim();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    requiredMessage
            );
        }

        return normalized;
    }
}