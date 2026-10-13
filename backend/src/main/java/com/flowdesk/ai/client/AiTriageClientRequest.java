package com.flowdesk.ai.client;

import com.flowdesk.ticket.domain.TicketType;

import java.util.Objects;

public record AiTriageClientRequest(

        TicketType type,
        String title,
        String description

) {

    public AiTriageClientRequest {
        Objects.requireNonNull(
                type,
                "Ticket type is required"
        );

        title =
                normalizeRequiredText(
                        title,
                        "Ticket title is required"
                );

        description =
                normalizeRequiredText(
                        description,
                        "Ticket description is required"
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