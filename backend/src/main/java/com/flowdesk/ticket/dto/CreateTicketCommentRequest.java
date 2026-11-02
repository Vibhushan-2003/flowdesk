package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.domain.CommentVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTicketCommentRequest(

    @NotBlank(message = "Comment body is required")
    @Size(
        max = 4000,
        message = "Comment body must not exceed 4000 characters"
    )
    String body,

    CommentVisibility visibility

) {
    public CreateTicketCommentRequest(String body) {
        this(body, CommentVisibility.PUBLIC);
    }
}
