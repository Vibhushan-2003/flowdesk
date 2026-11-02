package com.flowdesk.attachment.controller;

import com.flowdesk.attachment.dto.AttachmentRescanReport;
import com.flowdesk.attachment.service.TicketAttachmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative operations for attachments, including bounded batch rescanning of legacy pending files.
 */
@RestController
@RequestMapping("/api/admin/attachments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAttachmentController {

    private final TicketAttachmentService attachmentService;

    public AdminAttachmentController(TicketAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/rescan-pending")
    public ResponseEntity<AttachmentRescanReport> rescanPending(
            @RequestParam(name = "batchSize", defaultValue = "50") int batchSize
    ) {
        AttachmentRescanReport report = attachmentService.rescanPendingAttachments(batchSize);
        return ResponseEntity.ok(report);
    }
}

