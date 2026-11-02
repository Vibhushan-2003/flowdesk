package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.domain.TicketPriority;

import jakarta.validation.constraints.NotNull;

public record UpdateTicketPriorityRequest(

        @NotNull(message = "Priority is required")
        TicketPriority priority

) {
}

