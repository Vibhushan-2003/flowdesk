package com.flowdesk.ai.client;

public record AiResolutionSummaryClientResult(

        String draft,
        String provider,
        String model

) {

    private static final int MAX_DRAFT_LENGTH =
            2000;

    public AiResolutionSummaryClientResult {

        draft =
                normalizeRequiredText(
                        draft,
                        "AI resolution summary draft is required"
                );

        if (draft.length()
                > MAX_DRAFT_LENGTH) {

            throw new IllegalArgumentException(
                    "AI resolution summary draft must not exceed 2000 characters"
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