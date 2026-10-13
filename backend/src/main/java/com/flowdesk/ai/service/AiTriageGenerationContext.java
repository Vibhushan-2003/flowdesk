package com.flowdesk.ai.service;

import com.flowdesk.ticket.domain.TicketType;

import java.util.UUID;

record AiTriageGenerationContext(

        UUID ticketId,
        String ticketNumber,
        TicketType type,
        String title,
        String description

) {
}