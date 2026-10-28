package com.flowdesk.attachment.dto;

import java.util.UUID;

public record AttachmentDownloadDescriptor(
        UUID id,
        String ticketNumber,
        String filename,
        String contentType,
        long sizeBytes,
        String storageKey
) {
}
