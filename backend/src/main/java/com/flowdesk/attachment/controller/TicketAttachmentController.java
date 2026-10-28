package com.flowdesk.attachment.controller;

import com.flowdesk.attachment.dto.AttachmentDownloadDescriptor;
import com.flowdesk.attachment.dto.TicketAttachmentResponse;
import com.flowdesk.attachment.service.TicketAttachmentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets/{ticketNumber}/attachments")
public class TicketAttachmentController {

    private final TicketAttachmentService attachmentService;

    public TicketAttachmentController(TicketAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketAttachmentResponse> uploadAttachment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String ticketNumber,
            @RequestParam("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment file is required and cannot be empty");
        }

        UUID employeeId = UUID.fromString(jwt.getSubject());

        try (InputStream inputStream = file.getInputStream()) {
            TicketAttachmentResponse response = attachmentService.uploadAttachmentForEmployee(
                    employeeId,
                    ticketNumber,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    inputStream,
                    file.getSize()
            );

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read upload payload", e);
        }
    }

    @GetMapping
    public ResponseEntity<List<TicketAttachmentResponse>> getAttachments(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String ticketNumber
    ) {
        UUID employeeId = UUID.fromString(jwt.getSubject());
        List<TicketAttachmentResponse> response = attachmentService.getAttachmentsForEmployee(employeeId, ticketNumber);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String ticketNumber,
            @PathVariable UUID attachmentId
    ) {
        UUID employeeId = UUID.fromString(jwt.getSubject());
        AttachmentDownloadDescriptor descriptor = attachmentService.getAttachmentDownloadForEmployee(
                employeeId,
                ticketNumber,
                attachmentId
        );

        InputStream inputStream = attachmentService.loadAttachmentStream(descriptor);
        Resource resource = new InputStreamResource(inputStream);

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(descriptor.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(descriptor.contentType()))
                .contentLength(descriptor.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate, private")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .body(resource);
    }
}
