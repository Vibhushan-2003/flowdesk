package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiResolutionSummaryConversationMessage;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import java.util.List;
import java.util.UUID;

public record AiResolutionSummaryGenerationContext(

        UUID ticketId,
        String ticketNumber,
        TicketType type,
        TicketPriority priority,
        TicketStatus status,
        String title,
        String description,
        List<AiResolutionSummaryConversationMessage>
                conversation

) {

    public AiResolutionSummaryGenerationContext {

        conversation =
                List.copyOf(
                        conversation
                );
    }
}