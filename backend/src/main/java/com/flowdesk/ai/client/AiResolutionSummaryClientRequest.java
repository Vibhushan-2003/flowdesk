package com.flowdesk.ai.client;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import java.util.List;
import java.util.Objects;

public record AiResolutionSummaryClientRequest(

        TicketType type,
        TicketPriority priority,
        TicketStatus status,
        String title,
        String description,
        List<AiResolutionSummaryConversationMessage>
                conversation

) {

    public AiResolutionSummaryClientRequest {

        Objects.requireNonNull(
                type,
                "Ticket type is required"
        );

        Objects.requireNonNull(
                priority,
                "Ticket priority is required"
        );

        Objects.requireNonNull(
                status,
                "Ticket status is required"
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

        Objects.requireNonNull(
                conversation,
                "Conversation is required"
        );

        if (conversation
                .stream()
                .anyMatch(Objects::isNull)) {

            throw new IllegalArgumentException(
                    "Conversation must not contain null messages"
            );
        }

        conversation =
                List.copyOf(
                        conversation
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