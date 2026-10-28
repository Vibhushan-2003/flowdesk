package com.flowdesk.attachment.validation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum AllowedAttachmentType {

    PDF("application/pdf", List.of("pdf"), true),
    JPEG("image/jpeg", List.of("jpg", "jpeg"), true),
    PNG("image/png", List.of("png"), true),
    WEBP("image/webp", List.of("webp"), true),
    TXT("text/plain", List.of("txt"), false),
    LOG("text/plain", List.of("log"), false),
    CSV("text/csv", List.of("csv"), false);

    private final String canonicalMimeType;
    private final List<String> extensions;
    private final boolean binary;

    AllowedAttachmentType(String canonicalMimeType, List<String> extensions, boolean binary) {
        this.canonicalMimeType = canonicalMimeType;
        this.extensions = extensions;
        this.binary = binary;
    }

    public String getCanonicalMimeType() {
        return canonicalMimeType;
    }

    public List<String> getExtensions() {
        return Collections.unmodifiableList(extensions);
    }

    public String getPrimaryExtension() {
        return extensions.get(0);
    }

    public boolean isBinary() {
        return binary;
    }

    public static Optional<AllowedAttachmentType> fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return Optional.empty();
        }
        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        final String searchExt = normalized;
        return Arrays.stream(values())
                .filter(type -> type.extensions.contains(searchExt))
                .findFirst();
    }
}
