package com.flowdesk.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketCommentRequest(

    @NotBlank(message = "Comment body is required")
    @Size(
        max = 4000,
        message = "Comment body must not exceed 4000 characters"
    )
    String body

) {
}