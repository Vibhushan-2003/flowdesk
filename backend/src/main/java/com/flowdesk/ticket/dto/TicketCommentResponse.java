package com.flowdesk.ticket.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketCommentResponse(

    UUID commentId,

    UUID authorUserId,

    String authorName,

    String body,

    OffsetDateTime createdAt

) {
}