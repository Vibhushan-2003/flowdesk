package com.flowdesk.ticket.dto;

import com.flowdesk.ticket.domain.CommentVisibility;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketCommentResponse(

    UUID commentId,

    UUID authorUserId,

    String authorName,

    String body,

    OffsetDateTime createdAt,

    CommentVisibility visibility

) {
    public TicketCommentResponse(
        UUID commentId,
        UUID authorUserId,
        String authorName,
        String body,
        OffsetDateTime createdAt
    ) {
        this(commentId, authorUserId, authorName, body, createdAt, CommentVisibility.PUBLIC);
    }
}
