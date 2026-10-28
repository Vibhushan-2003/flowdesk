package com.flowdesk.attachment.storage;

import java.util.Objects;

public record StoredFile(
        String storageKey,
        long sizeBytes,
        String checksumSha256
) {
    public StoredFile {
        Objects.requireNonNull(storageKey, "storageKey is required");
        Objects.requireNonNull(checksumSha256, "checksumSha256 is required");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
        if (checksumSha256.length() != 64) {
            throw new IllegalArgumentException("checksumSha256 must be a 64-character SHA-256 hex string");
        }
    }
}
