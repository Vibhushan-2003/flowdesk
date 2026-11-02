package com.flowdesk.attachment.dto;

/**
 * Audit and operational report returned after an administrative pending attachments rescan batch.
 */
public record AttachmentRescanReport(
        long totalPending,
        int scannedCount,
        int cleanCount,
        int infectedCount,
        int failedCount,
        String message
) {
    public static AttachmentRescanReport skipped(long pending, String reason) {
        return new AttachmentRescanReport(pending, 0, 0, 0, 0, reason);
    }
}

