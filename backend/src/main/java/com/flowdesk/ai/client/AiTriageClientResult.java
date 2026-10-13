package com.flowdesk.ai.client;

import com.flowdesk.ticket.domain.TicketPriority;

import java.math.BigDecimal;

import java.util.Objects;

public record AiTriageClientResult(

        TicketPriority suggestedPriority,
        BigDecimal confidence,
        String reason,
        String provider,
        String model

) {

    public AiTriageClientResult {
        Objects.requireNonNull(
                suggestedPriority,
                "Suggested priority is required"
        );

        Objects.requireNonNull(
                confidence,
                "AI confidence is required"
        );

        if (confidence.compareTo(
                BigDecimal.ZERO
        ) < 0
                || confidence.compareTo(
                        BigDecimal.ONE
                ) > 0) {

            throw new IllegalArgumentException(
                    "AI confidence must be between 0 and 1"
            );
        }

        reason =
                normalizeRequiredText(
                        reason,
                        "AI triage reason is required"
                );

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