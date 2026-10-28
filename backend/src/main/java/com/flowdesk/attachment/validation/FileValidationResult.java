package com.flowdesk.attachment.validation;

import java.util.Objects;

public record FileValidationResult(
        String sanitizedFilename,
        AllowedAttachmentType allowedType,
        String canonicalContentType,
        String extension,
        long sizeBytes,
        String checksumSha256
) {
    public FileValidationResult {
        Objects.requireNonNull(sanitizedFilename, "sanitizedFilename is required");
        Objects.requireNonNull(allowedType, "allowedType is required");
        Objects.requireNonNull(canonicalContentType, "canonicalContentType is required");
        Objects.requireNonNull(extension, "extension is required");
        Objects.requireNonNull(checksumSha256, "checksumSha256 is required");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be greater than zero");
        }
        if (checksumSha256.length() != 64) {
            throw new IllegalArgumentException("checksumSha256 must be a 64-character SHA-256 hex string");
        }
    }
}
