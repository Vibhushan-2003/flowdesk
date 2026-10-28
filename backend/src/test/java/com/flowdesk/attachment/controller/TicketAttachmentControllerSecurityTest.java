package com.flowdesk.attachment.controller;

import com.flowdesk.attachment.dto.AttachmentDownloadDescriptor;
import com.flowdesk.attachment.dto.TicketAttachmentResponse;
import com.flowdesk.attachment.service.TicketAttachmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketAttachmentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketAttachmentService attachmentService;

    @Test
    @DisplayName("Guest (unauthenticated) cannot access employee attachment upload")
    void guestCannotUploadAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4 test".getBytes()
        );

        mockMvc.perform(multipart("/api/tickets/FD-000101/attachments").file(file))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(attachmentService);
    }

    @Test
    @DisplayName("Guest (unauthenticated) cannot list employee attachments")
    void guestCannotListAttachments() throws Exception {
        mockMvc.perform(get("/api/tickets/FD-000101/attachments"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(attachmentService);
    }

    @Test
    @DisplayName("Guest (unauthenticated) cannot download employee attachment")
    void guestCannotDownloadAttachment() throws Exception {
        mockMvc.perform(get("/api/tickets/FD-000101/attachments/" + UUID.randomUUID() + "/download"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(attachmentService);
    }

    @Test
    @DisplayName("Employee cannot access support attachment endpoints (403 Forbidden)")
    void employeeCannotAccessSupportEndpoints() throws Exception {
        UUID employeeId = UUID.randomUUID();

        mockMvc.perform(get("/api/support/tickets/FD-000101/attachments")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(attachmentService);
    }

    @Test
    @DisplayName("Authorized employee can upload attachment (201 Created)")
    void employeeCanUploadAttachment() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "vpn_log.txt", "text/plain", "log data".getBytes(StandardCharsets.UTF_8)
        );

        TicketAttachmentResponse response = new TicketAttachmentResponse(
                attachmentId,
                "FD-000101",
                "vpn_log.txt",
                "text/plain",
                8L,
                employeeId,
                "Alice Engineer",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(attachmentService.uploadAttachmentForEmployee(
                eq(employeeId), eq("FD-000101"), eq("vpn_log.txt"), eq("text/plain"), any(InputStream.class), eq(8L)
        )).thenReturn(response);

        mockMvc.perform(multipart("/api/tickets/FD-000101/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(attachmentId.toString())))
                .andExpect(jsonPath("$.filename", is("vpn_log.txt")))
                .andExpect(jsonPath("$.contentType", is("text/plain")))
                .andExpect(jsonPath("$.sizeBytes", is(8)))
                .andExpect(jsonPath("$.uploadedByName", is("Alice Engineer")))
                // Internal storage key must NEVER be exposed
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    @DisplayName("Authorized employee can list AVAILABLE attachments")
    void employeeCanListAttachments() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        TicketAttachmentResponse item = new TicketAttachmentResponse(
                attachmentId,
                "FD-000101",
                "spec.pdf",
                "application/pdf",
                1024L,
                employeeId,
                "Alice",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(attachmentService.getAttachmentsForEmployee(employeeId, "FD-000101"))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/tickets/FD-000101/attachments")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(attachmentId.toString())))
                .andExpect(jsonPath("$[0].filename", is("spec.pdf")))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());
    }

    @Test
    @DisplayName("Authorized employee download streams binary with secure headers")
    void employeeDownloadStreamsWithSecureHeaders() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        byte[] payload = "Downloaded report payload".getBytes(StandardCharsets.UTF_8);

        AttachmentDownloadDescriptor descriptor = new AttachmentDownloadDescriptor(
                attachmentId,
                "FD-000101",
                "report.txt",
                "text/plain",
                payload.length,
                "opaque-key-1234.dat"
        );

        when(attachmentService.getAttachmentDownloadForEmployee(employeeId, "FD-000101", attachmentId))
                .thenReturn(descriptor);
        when(attachmentService.loadAttachmentStream(descriptor))
                .thenReturn(new ByteArrayInputStream(payload));

        mockMvc.perform(get("/api/tickets/FD-000101/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment; filename=\"report.txt\"")))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/plain")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, payload.length))
                .andExpect(content().bytes(payload));
    }

    @Test
    @DisplayName("Finished ticket returns 409 Conflict on upload")
    void finishedTicketReturns409OnUpload() throws Exception {
        UUID employeeId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "log.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8)
        );

        when(attachmentService.uploadAttachmentForEmployee(any(), any(), any(), any(), any(), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Attachments cannot be added to a finished ticket"));

        mockMvc.perform(multipart("/api/tickets/FD-000101/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("finished ticket")));
    }

    @Test
    @DisplayName("Quota exceeded returns 409 Conflict on upload")
    void quotaExceededReturns409OnUpload() throws Exception {
        UUID employeeId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "log.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8)
        );

        when(attachmentService.uploadAttachmentForEmployee(any(), any(), any(), any(), any(), anyLong()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Ticket attachment quota exceeded"));

        mockMvc.perform(multipart("/api/tickets/FD-000101/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("quota exceeded")));
    }

    @Test
    @DisplayName("Cross-employee ticket access returns 404 Not Found")
    void crossEmployeeAccessReturns404() throws Exception {
        UUID employeeB = UUID.randomUUID();

        when(attachmentService.getAttachmentsForEmployee(employeeB, "FD-000101"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        mockMvc.perform(get("/api/tickets/FD-000101/attachments")
                        .with(jwt().jwt(j -> j.subject(employeeB.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Ticket not found")));
    }

    @Test
    @DisplayName("Support engineer can access support attachment APIs with active assignment")
    void supportEngineerCanAccessSupportEndpoints() throws Exception {
        UUID supportId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        TicketAttachmentResponse item = new TicketAttachmentResponse(
                attachmentId,
                "FD-000101",
                "diag.png",
                "image/png",
                2048L,
                supportId,
                "Bob Support",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(attachmentService.getAttachmentsForSupport(supportId, "FD-000101"))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/support/tickets/FD-000101/attachments")
                        .with(jwt().jwt(j -> j.subject(supportId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename", is("diag.png")));
    }

    @Test
    @DisplayName("Empty file upload returns 400 Bad Request")
    void emptyFileUploadReturns400() throws Exception {
        UUID employeeId = UUID.randomUUID();
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]
        );

        mockMvc.perform(multipart("/api/tickets/FD-000101/attachments")
                        .file(emptyFile)
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("cannot be empty")));
    }

    @Test
    @DisplayName("Support engineer can upload attachment on assigned ticket (201 Created)")
    void supportEngineerCanUploadAttachment() throws Exception {
        UUID supportId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "patch.txt", "text/plain", "patch instructions".getBytes(StandardCharsets.UTF_8)
        );

        TicketAttachmentResponse response = new TicketAttachmentResponse(
                attachmentId,
                "FD-000101",
                "patch.txt",
                "text/plain",
                18L,
                supportId,
                "Bob Support",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        when(attachmentService.uploadAttachmentForSupport(
                eq(supportId), eq("FD-000101"), eq("patch.txt"), eq("text/plain"), any(InputStream.class), eq(18L)
        )).thenReturn(response);

        mockMvc.perform(multipart("/api/support/tickets/FD-000101/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(supportId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(attachmentId.toString())))
                .andExpect(jsonPath("$.filename", is("patch.txt")))
                .andExpect(jsonPath("$.storageKey").doesNotExist());
    }

    @Test
    @DisplayName("Support engineer can download attachment from assigned ticket")
    void supportEngineerCanDownloadAttachment() throws Exception {
        UUID supportId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        byte[] payload = "Support patch content".getBytes(StandardCharsets.UTF_8);

        AttachmentDownloadDescriptor descriptor = new AttachmentDownloadDescriptor(
                attachmentId,
                "FD-000101",
                "patch.txt",
                "text/plain",
                payload.length,
                "support-storage-key.dat"
        );

        when(attachmentService.getAttachmentDownloadForSupport(supportId, "FD-000101", attachmentId))
                .thenReturn(descriptor);
        when(attachmentService.loadAttachmentStream(descriptor))
                .thenReturn(new ByteArrayInputStream(payload));

        mockMvc.perform(get("/api/support/tickets/FD-000101/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(supportId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(payload));
    }

    @Test
    @DisplayName("Released support assignment denies access (404 Not Found)")
    void releasedSupportAssignmentReturns404() throws Exception {
        UUID supportId = UUID.randomUUID();

        when(attachmentService.getAttachmentsForSupport(supportId, "FD-000101"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

        mockMvc.perform(get("/api/support/tickets/FD-000101/attachments")
                        .with(jwt().jwt(j -> j.subject(supportId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Ticket not found")));
    }

    @Test
    @DisplayName("Mismatched ticketNumber and attachmentId returns 404 Not Found")
    void wrongTicketAndAttachmentCombinationReturns404() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(attachmentService.getAttachmentDownloadForEmployee(employeeId, "FD-000101", attachmentId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        mockMvc.perform(get("/api/tickets/FD-000101/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Attachment not found")));
    }

    @Test
    @DisplayName("Missing physical file on disk returns 404 Not Found without leaking paths")
    void missingPhysicalFileReturns404() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();

        when(attachmentService.getAttachmentDownloadForEmployee(employeeId, "FD-000101", attachmentId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file is unavailable"));

        mockMvc.perform(get("/api/tickets/FD-000101/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Attachment file is unavailable")))
                .andExpect(jsonPath("$.message", not(containsString("C:\\"))))
                .andExpect(jsonPath("$.message", not(containsString(".dat"))));
    }
}
