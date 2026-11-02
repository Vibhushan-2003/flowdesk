package com.flowdesk.attachment.domain;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private User uploadedByUser;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "sanitized_filename", nullable = false, length = 255)
    private String sanitizedFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AttachmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 30)
    private ScanStatus scanStatus;

    @Column(name = "scanned_at")
    private OffsetDateTime scannedAt;

    @Column(name = "scan_details", length = 255)
    private String scanDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TicketAttachment() {
    }

    public TicketAttachment(
            Ticket ticket,
            User uploadedByUser,
            String originalFilename,
            String sanitizedFilename,
            String contentType,
            long sizeBytes,
            String storageKey,
            String checksumSha256,
            AttachmentStatus status
    ) {
        this(ticket, uploadedByUser, originalFilename, sanitizedFilename, contentType, sizeBytes, storageKey, checksumSha256, status, ScanStatus.CLEAN);
    }

    public TicketAttachment(
            Ticket ticket,
            User uploadedByUser,
            String originalFilename,
            String sanitizedFilename,
            String contentType,
            long sizeBytes,
            String storageKey,
            String checksumSha256,
            AttachmentStatus status,
            ScanStatus scanStatus
    ) {
        this.id = UUID.randomUUID();
        this.ticket = Objects.requireNonNull(ticket, "ticket is required");
        this.uploadedByUser = Objects.requireNonNull(uploadedByUser, "uploadedByUser is required");
        this.originalFilename = validateNonBlank(originalFilename, "originalFilename");
        this.sanitizedFilename = validateNonBlank(sanitizedFilename, "sanitizedFilename");
        this.contentType = validateNonBlank(contentType, "contentType");
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be greater than zero");
        }
        this.sizeBytes = sizeBytes;
        this.storageKey = validateNonBlank(storageKey, "storageKey");
        if (checksumSha256 == null || checksumSha256.length() != 64) {
            throw new IllegalArgumentException("checksumSha256 must be a 64-character SHA-256 string");
        }
        this.checksumSha256 = checksumSha256;
        this.status = status != null ? status : AttachmentStatus.AVAILABLE;
        this.scanStatus = scanStatus != null ? scanStatus : ScanStatus.CLEAN;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = AttachmentStatus.AVAILABLE;
        }
        if (scanStatus == null) {
            scanStatus = ScanStatus.CLEAN;
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    private static String validateNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    public void markClean() {
        this.scanStatus = ScanStatus.CLEAN;
        this.status = AttachmentStatus.AVAILABLE;
        this.scannedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.scanDetails = "Clean";
    }

    public void markInfected(String virusName) {
        this.scanStatus = ScanStatus.INFECTED;
        this.status = AttachmentStatus.QUARANTINED;
        this.scannedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.scanDetails = virusName != null ? virusName : "Malware.Detected";
    }

    public void markScanFailed(String reason) {
        this.scanStatus = ScanStatus.SCAN_FAILED;
        this.status = AttachmentStatus.QUARANTINED;
        this.scannedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.scanDetails = reason != null ? reason : "Scanner failure";
    }

    public boolean isDownloadable() {
        return this.status == AttachmentStatus.AVAILABLE && this.scanStatus == ScanStatus.CLEAN;
    }

    public void markAvailable() {
        this.status = AttachmentStatus.AVAILABLE;
    }

    public void markDeleted() {
        this.status = AttachmentStatus.DELETED;
    }

    public UUID getId() {
        return id;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getUploadedByUser() {
        return uploadedByUser;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getSanitizedFilename() {
        return sanitizedFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public AttachmentStatus getStatus() {
        return status;
    }

    public ScanStatus getScanStatus() {
        return scanStatus;
    }

    public OffsetDateTime getScannedAt() {
        return scannedAt;
    }

    public String getScanDetails() {
        return scanDetails;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
