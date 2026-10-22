package com.flowdesk.ai.tool;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

public record AiSupportCopilotTicketOverview(

        TicketType type,

        TicketPriority priority,

        TicketStatus status,

        String title,

        String description

) {
}