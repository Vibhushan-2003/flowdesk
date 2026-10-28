package com.flowdesk.attachment.service;

import com.flowdesk.attachment.domain.AttachmentStatus;
import com.flowdesk.attachment.domain.TicketAttachment;
import com.flowdesk.attachment.dto.AttachmentDownloadDescriptor;
import com.flowdesk.attachment.dto.TicketAttachmentResponse;
import com.flowdesk.attachment.repository.TicketAttachmentRepository;
import com.flowdesk.attachment.storage.AttachmentStorageProperties;
import com.flowdesk.attachment.storage.AttachmentStorageService;
import com.flowdesk.attachment.storage.StorageException;
import com.flowdesk.attachment.storage.StoredFile;
import com.flowdesk.attachment.validation.FileContentValidator;
import com.flowdesk.attachment.validation.FileValidationException;
import com.flowdesk.attachment.validation.FileValidationResult;
import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TicketAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketAttachmentService.class);

    private final TicketRepository ticketRepository;
    private final TicketAssignmentRepository ticketAssignmentRepository;
    private final UserRepository userRepository;
    private final TicketAttachmentRepository attachmentRepository;
    private final AttachmentStorageService storageService;
    private final FileContentValidator fileContentValidator;
    private final AttachmentStorageProperties properties;
    private final AuditService auditService;

    public TicketAttachmentService(
            TicketRepository ticketRepository,
            TicketAssignmentRepository ticketAssignmentRepository,
            UserRepository userRepository,
            TicketAttachmentRepository attachmentRepository,
            AttachmentStorageService storageService,
            FileContentValidator fileContentValidator,
            AttachmentStorageProperties properties,
            AuditService auditService
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketAssignmentRepository = ticketAssignmentRepository;
        this.userRepository = userRepository;
        this.attachmentRepository = attachmentRepository;
        this.storageService = storageService;
        this.fileContentValidator = fileContentValidator;
        this.properties = properties;
        this.auditService = auditService;
    }

    /**
     * Uploads an attachment to a ticket on behalf of an employee requester.
     */
    @Transactional
    public TicketAttachmentResponse uploadAttachmentForEmployee(
            UUID employeeId,
            String ticketNumber,
            String originalFilename,
            String declaredContentType,
            InputStream contentStream,
            long declaredSize
    ) {
        String normalizedTicketNumber = normalizeTicketNumber(ticketNumber);
        User actor = findUser(employeeId);

        // Fast pre-check before touching storage
        Ticket preTicket = ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(normalizedTicketNumber, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        ensureUploadAllowed(preTicket);

        return processUpload(actor, normalizedTicketNumber, originalFilename, declaredContentType, contentStream, declaredSize);
    }

    /**
     * Uploads an attachment to a ticket on behalf of an actively assigned support engineer.
     */
    @Transactional
    public TicketAttachmentResponse uploadAttachmentForSupport(
            UUID supportEngineerId,
            String ticketNumber,
            String originalFilename,
            String declaredContentType,
            InputStream contentStream,
            long declaredSize
    ) {
        String normalizedTicketNumber = normalizeTicketNumber(ticketNumber);
        User actor = findUser(supportEngineerId);

        // Fast pre-check before touching storage
        TicketAssignment assignment = findAuthorizedSupportAssignment(normalizedTicketNumber, supportEngineerId);
        ensureUploadAllowed(assignment.getTicket());

        return processUpload(actor, normalizedTicketNumber, originalFilename, declaredContentType, contentStream, declaredSize);
    }

    /**
     * Lists all AVAILABLE attachments for an employee's ticket.
     */
    @Transactional(readOnly = true)
    public List<TicketAttachmentResponse> getAttachmentsForEmployee(
            UUID employeeId,
            String ticketNumber
    ) {
        String normalizedTicketNumber = normalizeTicketNumber(ticketNumber);
        Ticket ticket = ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(normalizedTicketNumber, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        return listAvailableAttachments(ticket);
    }

    /**
     * Lists all AVAILABLE attachments for an authorized support engineer's ticket.
     */
    @Transactional(readOnly = true)
    public List<TicketAttachmentResponse> getAttachmentsForSupport(
            UUID supportEngineerId,
            String ticketNumber
    ) {
        String normalizedTicketNumber = normalizeTicketNumber(ticketNumber);
        TicketAssignment assignment = findAuthorizedSupportAssignment(normalizedTicketNumber, supportEngineerId);

        return listAvailableAttachments(assignment.getTicket());
    }

    /**
     * Retrieves an authorized download descriptor for an employee's ticket attachment.
     */
    @Transactional
    public AttachmentDownloadDescriptor getAttachmentDownloadForEmployee(
            UUID employeeId,
            String ticketNumber,
            UUID attachmentId
    ) {
        String normalizedTicketNumber = normalizeTicketNumber(ticketNumber);
        User actor = findUser(employeeId);

        Ticket ticket = ticketRepository
                .findByTicketNumberAndCreatedByUser_Id(normalizedTicketNumber, employeeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        return prepareDownloadDescriptor(actor, ticket, attachmentId);
    }

    /**
     * Retrieves an authorized download descriptor for an authorized support engineer's ticket attachment.
     */
    @Transactional
    public AttachmentDownloadDescriptor getAttachmentDownloadForSupport(
            UUID supportEngineerId,
            String ticketNumber,
            UUID attachmentId
    ) {
        String normalizedTicketNumber = normalizeTicketNumber(ticketNumber);
        User actor = findUser(supportEngineerId);

        TicketAssignment assignment = findAuthorizedSupportAssignment(normalizedTicketNumber, supportEngineerId);

        return prepareDownloadDescriptor(actor, assignment.getTicket(), attachmentId);
    }

    /**
     * Loads the raw attachment stream for an authorized download descriptor.
     */
    public InputStream loadAttachmentStream(AttachmentDownloadDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor is required");
        try {
            return storageService.load(descriptor.storageKey());
        } catch (StorageException e) {
            log.error("Failed to load attachment file [storageKey={}]", descriptor.storageKey(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read attachment file", e);
        }
    }

    private TicketAttachmentResponse processUpload(
            User actor,
            String normalizedTicketNumber,
            String originalFilename,
            String declaredContentType,
            InputStream contentStream,
            long declaredSize
    ) {
        StoredFile storedFile = null;
        try {
            // 1. Stream into private storage under opaque key with bounded memory
            try {
                storedFile = storageService.store(contentStream, declaredSize);
            } catch (StorageException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            }

            // 2. Validate file signatures, structural compliance, and sanitize filename
            FileValidationResult validation;
            try (InputStream storedIn = storageService.load(storedFile.storageKey())) {
                validation = fileContentValidator.validate(originalFilename, declaredContentType, storedIn, storedFile.sizeBytes());
            } catch (FileValidationException e) {
                storageService.delete(storedFile.storageKey());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
            } catch (Exception e) {
                storageService.delete(storedFile.storageKey());
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File validation failed: " + e.getMessage(), e);
            }

            final String storageKey = storedFile.storageKey();

            // 3. Register transaction synchronization to delete physical file if DB commit fails
            registerCompensatoryStorageCleanup(storageKey);

            // 4. Acquire pessimistic write lock on ticket to serialize competing uploads
            Ticket ticket = ticketRepository.findByTicketNumberForUpdate(normalizedTicketNumber)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

            ensureUploadAllowed(ticket);

            // 5. Enforce attachment quota under lock
            long activeCount = attachmentRepository.countByTicket_IdAndStatusIn(
                    ticket.getId(),
                    List.of(AttachmentStatus.AVAILABLE, AttachmentStatus.PENDING)
            );
            int maxAllowed = properties.getMaxAttachmentsPerTicket();
            if (activeCount >= maxAllowed) {
                storageService.delete(storageKey);
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ticket attachment quota exceeded (maximum " + maxAllowed + " attachments per ticket)"
                );
            }

            // 6. Persist metadata record in PostgreSQL
            TicketAttachment attachment = new TicketAttachment(
                    ticket,
                    actor,
                    originalFilename,
                    validation.sanitizedFilename(),
                    validation.canonicalContentType(),
                    validation.sizeBytes(),
                    storageKey,
                    validation.checksumSha256(),
                    AttachmentStatus.AVAILABLE
            );

            TicketAttachment saved = attachmentRepository.saveAndFlush(attachment);

            // 7. Record durable audit log
            auditService.recordUserAction(
                    actor,
                    AuditAction.TICKET_ATTACHMENT_UPLOADED,
                    AuditTargetType.TICKET,
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    Map.of(
                            "attachmentId", saved.getId().toString(),
                            "filename", saved.getSanitizedFilename(),
                            "contentType", saved.getContentType(),
                            "sizeBytes", saved.getSizeBytes()
                    )
            );

            return toResponse(saved);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            if (storedFile != null) {
                try {
                    storageService.delete(storedFile.storageKey());
                } catch (Exception ignored) {
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to complete attachment upload", e);
        }
    }

    private void registerCompensatoryStorageCleanup(String storageKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        log.warn("Attachment transaction failed to commit. Compensating by deleting file [storageKey={}]", storageKey);
                        try {
                            storageService.delete(storageKey);
                        } catch (Exception ex) {
                            log.error("Failed to delete orphaned attachment file [storageKey={}] during rollback compensation", storageKey, ex);
                        }
                    }
                }
            });
        }
    }

    private AttachmentDownloadDescriptor prepareDownloadDescriptor(
            User actor,
            Ticket ticket,
            UUID attachmentId
    ) {
        Objects.requireNonNull(attachmentId, "attachmentId is required");

        TicketAttachment attachment = attachmentRepository
                .findByTicket_TicketNumberAndIdAndStatus(ticket.getTicketNumber(), attachmentId, AttachmentStatus.AVAILABLE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        if (!storageService.exists(attachment.getStorageKey())) {
            log.error("Attachment record [id={}] points to missing physical file [storageKey={}]", attachment.getId(), attachment.getStorageKey());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file is unavailable");
        }

        auditService.recordUserAction(
                actor,
                AuditAction.TICKET_ATTACHMENT_DOWNLOADED,
                AuditTargetType.TICKET,
                ticket.getId(),
                ticket.getTicketNumber(),
                Map.of(
                        "attachmentId", attachment.getId().toString(),
                        "filename", attachment.getSanitizedFilename(),
                        "contentType", attachment.getContentType(),
                        "sizeBytes", attachment.getSizeBytes()
                )
        );

        return new AttachmentDownloadDescriptor(
                attachment.getId(),
                ticket.getTicketNumber(),
                attachment.getSanitizedFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                attachment.getStorageKey()
        );
    }

    private List<TicketAttachmentResponse> listAvailableAttachments(Ticket ticket) {
        return attachmentRepository
                .findByTicket_IdAndStatusOrderByCreatedAtAsc(ticket.getId(), AttachmentStatus.AVAILABLE)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void ensureUploadAllowed(Ticket ticket) {
        TicketStatus status = ticket.getStatus();
        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED || status == TicketStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Attachments cannot be added to a finished ticket"
            );
        }
    }

    private TicketAssignment findAuthorizedSupportAssignment(String normalizedTicketNumber, UUID supportEngineerId) {
        return ticketAssignmentRepository
                .findAuthorizedSupportAssignments(normalizedTicketNumber, supportEngineerId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private String normalizeTicketNumber(String ticketNumber) {
        if (ticketNumber == null || ticketNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket number is required");
        }
        return ticketNumber.trim().toUpperCase(Locale.ROOT);
    }

    private TicketAttachmentResponse toResponse(TicketAttachment attachment) {
        User uploader = attachment.getUploadedByUser();
        String uploaderName = (uploader.getFirstName() + " " + uploader.getLastName()).trim();
        if (uploaderName.isEmpty()) {
            uploaderName = uploader.getEmail();
        }

        return new TicketAttachmentResponse(
                attachment.getId(),
                attachment.getTicket().getTicketNumber(),
                attachment.getSanitizedFilename(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                uploader.getId(),
                uploaderName,
                attachment.getCreatedAt()
        );
    }
}
