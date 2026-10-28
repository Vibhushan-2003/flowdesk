package com.flowdesk.attachment.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConfigurationProperties(prefix = "app.storage.attachment")
public class AttachmentStorageProperties {

    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L; // 10 MB
    public static final int DEFAULT_MAX_ATTACHMENTS_PER_TICKET = 10;
    public static final String DEFAULT_BASE_DIRECTORY = "data/attachments";

    private Local local = new Local();
    private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
    private int maxAttachmentsPerTicket = DEFAULT_MAX_ATTACHMENTS_PER_TICKET;

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be greater than zero");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getMaxAttachmentsPerTicket() {
        return maxAttachmentsPerTicket;
    }

    public void setMaxAttachmentsPerTicket(int maxAttachmentsPerTicket) {
        if (maxAttachmentsPerTicket <= 0) {
            throw new IllegalArgumentException("maxAttachmentsPerTicket must be greater than zero");
        }
        this.maxAttachmentsPerTicket = maxAttachmentsPerTicket;
    }

    public static class Local {
        private String baseDirectory = DEFAULT_BASE_DIRECTORY;

        public String getBaseDirectory() {
            return baseDirectory;
        }

        public void setBaseDirectory(String baseDirectory) {
            if (baseDirectory == null || baseDirectory.isBlank()) {
                throw new IllegalArgumentException("baseDirectory cannot be blank");
            }
            this.baseDirectory = baseDirectory.trim();
        }

        public Path getBasePath() {
            return Paths.get(baseDirectory).normalize().toAbsolutePath();
        }
    }
}
