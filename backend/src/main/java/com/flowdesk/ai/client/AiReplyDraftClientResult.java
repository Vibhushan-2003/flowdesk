package com.flowdesk.ai.client;

public record AiReplyDraftClientResult(

        String draft,
        String provider,
        String model

) {

    private static final int MAX_DRAFT_LENGTH =
            2000;

    public AiReplyDraftClientResult {

        draft =
                normalizeRequiredText(
                        draft,
                        "AI reply draft is required"
                );

        if (draft.length()
                > MAX_DRAFT_LENGTH) {

            throw new IllegalArgumentException(
                    "AI reply draft must not exceed 2000 characters"
            );
        }

        provider =
                normalizeRequiredText(
                        provider,
                        "AI provider is required"
                );

        model =
                normalizeRequiredText(
                        model,
                        "AI model is required"
                );
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