package com.flowdesk.attachment.dto;

import com.flowdesk.attachment.domain.ScanStatus;

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
        OffsetDateTime createdAt,
        ScanStatus scanStatus,
        String scanDetails
) {
    public TicketAttachmentResponse(
            UUID id,
            String ticketNumber,
            String filename,
            String contentType,
            long sizeBytes,
            UUID uploadedByUserId,
            String uploadedByName,
            OffsetDateTime createdAt
    ) {
        this(id, ticketNumber, filename, contentType, sizeBytes, uploadedByUserId, uploadedByName, createdAt, ScanStatus.CLEAN, null);
    }
}
