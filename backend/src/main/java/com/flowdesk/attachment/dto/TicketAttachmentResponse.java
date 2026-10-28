package com.flowdesk.attachment.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TicketAttachmentResponse(
        UUID id,
        String ticketNumber,
        String filename,
        String contentType,
        long sizeBytes,
        UUID uploadedByUserId,
        String uploadedByName,
        OffsetDateTime createdAt
) {
}
