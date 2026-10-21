package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiReplyDraftConversationMessage;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import java.util.List;
import java.util.UUID;

public record AiReplyDraftGenerationContext(

        UUID ticketId,
        String ticketNumber,
        TicketType type,
        TicketPriority priority,
        TicketStatus status,
        String title,
        String description,
        List<AiReplyDraftConversationMessage>
                conversation

) {

    public AiReplyDraftGenerationContext {

        conversation =
                List.copyOf(
                        conversation
                );
    }
}