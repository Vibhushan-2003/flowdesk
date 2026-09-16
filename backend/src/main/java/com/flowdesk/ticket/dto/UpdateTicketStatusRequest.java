package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.domain.TicketStatus;

import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(

        @NotNull(message = "Status is required")
        TicketStatus status

) {
}