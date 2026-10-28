package com.flowdesk.attachment.service;

import com.flowdesk.attachment.domain.AttachmentStatus;
import com.flowdesk.attachment.domain.TicketAttachment;
import com.flowdesk.attachment.dto.AttachmentDownloadDescriptor;
import com.flowdesk.attachment.dto.TicketAttachmentResponse;
import com.flowdesk.attachment.repository.TicketAttachmentRepository;
import com.flowdesk.attachment.storage.AttachmentStorageService;
import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.repository.AuditLogRepository;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@DisplayName("TicketAttachmentIntegrationTest - Database & Service Integration")
class TicketAttachmentIntegrationTest {

    @Autowired
    private TicketAttachmentService attachmentService;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AttachmentStorageService storageService;

    private User employeeA;
    private User employeeB;
    private User supportEngineer;
    private Ticket ticketA;
    private final List<String> createdStorageKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));

        employeeA = createUser("emp-a", employeeRole);
        employeeB = createUser("emp-b", employeeRole);
        supportEngineer = createUser("support-eng", supportRole);

        ticketA = createTicket(employeeA, "FD-IT-101", "VPN issue");
    }

    @AfterEach
    void tearDown() {
        for (String key : createdStorageKeys) {
            try {
                storageService.delete(key);
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("Should successfully upload and persist attachment in PostgreSQL with audit log")
    void shouldUploadAndPersistAttachment() {
        byte[] content = "2026-10-28 12:00:00 [ERROR] Connection timed out\n".getBytes(StandardCharsets.UTF_8);

        TicketAttachmentResponse response = attachmentService.uploadAttachmentForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber(),
                "error.log",
                "text/plain",
                new ByteArrayInputStream(content),
                content.length
        );

        assertNotNull(response);
        assertNotNull(response.id());
        assertEquals("error.log", response.filename());
        assertEquals(content.length, response.sizeBytes());

        // Verify database persistence
        TicketAttachment persisted = attachmentRepository.findById(response.id()).orElse(null);
        assertNotNull(persisted);
        assertEquals(AttachmentStatus.AVAILABLE, persisted.getStatus());
        assertEquals(ticketA.getId(), persisted.getTicket().getId());
        createdStorageKeys.add(persisted.getStorageKey());

        // Verify storage file exists
        assertTrue(storageService.exists(persisted.getStorageKey()));

        // Verify audit log entry
        boolean hasAudit = auditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() == AuditAction.TICKET_ATTACHMENT_UPLOADED
                        && ticketA.getId().equals(log.getTargetId()));
        assertTrue(hasAudit, "Audit log for TICKET_ATTACHMENT_UPLOADED should be recorded");
    }

    @Test
    @DisplayName("Cross-user isolation: Employee B cannot list or download Employee A's attachments")
    void crossUserIsolationShouldDenyAccess() {
        byte[] content = "Sensitive diagnostic information".getBytes(StandardCharsets.UTF_8);
        TicketAttachmentResponse uploaded = attachmentService.uploadAttachmentForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber(),
                "diagnostic.txt",
                "text/plain",
                new ByteArrayInputStream(content),
                content.length
        );
        createdStorageKeys.add(attachmentRepository.findById(uploaded.id()).get().getStorageKey());

        // Employee B listing Employee A's attachments -> 404
        ResponseStatusException listEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentsForEmployee(employeeB.getId(), ticketA.getTicketNumber())
        );
        assertEquals(HttpStatus.NOT_FOUND, listEx.getStatusCode());

        // Employee B downloading Employee A's attachment -> 404
        ResponseStatusException downloadEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentDownloadForEmployee(employeeB.getId(), ticketA.getTicketNumber(), uploaded.id())
        );
        assertEquals(HttpStatus.NOT_FOUND, downloadEx.getStatusCode());
    }

    @Test
    @DisplayName("Support engineer without active assignment cannot access attachments (404)")
    void unassignedSupportEngineerDenied() {
        byte[] content = "Data file".getBytes(StandardCharsets.UTF_8);
        TicketAttachmentResponse uploaded = attachmentService.uploadAttachmentForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber(),
                "data.csv",
                "text/csv",
                new ByteArrayInputStream(content),
                content.length
        );
        createdStorageKeys.add(attachmentRepository.findById(uploaded.id()).get().getStorageKey());

        // Not assigned -> 404
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentsForSupport(supportEngineer.getId(), ticketA.getTicketNumber())
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("Actively assigned support engineer can access and upload attachments")
    void assignedSupportEngineerCanAccessAndUpload() {
        // Assign ticket to support engineer
        TicketAssignment assignment = new TicketAssignment(ticketA, supportEngineer);
        ticketAssignmentRepository.saveAndFlush(assignment);

        byte[] engineerContent = "Fix applied.\n".getBytes(StandardCharsets.UTF_8);
        TicketAttachmentResponse uploaded = attachmentService.uploadAttachmentForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber(),
                "fix_notes.txt",
                "text/plain",
                new ByteArrayInputStream(engineerContent),
                engineerContent.length
        );

        assertNotNull(uploaded);
        createdStorageKeys.add(attachmentRepository.findById(uploaded.id()).get().getStorageKey());

        // Support engineer can list
        List<TicketAttachmentResponse> list = attachmentService.getAttachmentsForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber()
        );
        assertEquals(1, list.size());

        // Support engineer can download
        AttachmentDownloadDescriptor descriptor = attachmentService.getAttachmentDownloadForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber(),
                uploaded.id()
        );
        assertNotNull(descriptor);
        assertEquals("fix_notes.txt", descriptor.filename());
    }

    @Test
    @DisplayName("Released assignment revokes support engineer access (404)")
    void releasedAssignmentRevokesAccess() {
        TicketAssignment assignment = new TicketAssignment(ticketA, supportEngineer);
        ticketAssignmentRepository.saveAndFlush(assignment);

        byte[] content = "Trace file".getBytes(StandardCharsets.UTF_8);
        TicketAttachmentResponse uploaded = attachmentService.uploadAttachmentForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber(),
                "trace.log",
                "text/plain",
                new ByteArrayInputStream(content),
                content.length
        );
        createdStorageKeys.add(attachmentRepository.findById(uploaded.id()).get().getStorageKey());

        // Release the assignment
        assignment.release();
        ticketAssignmentRepository.saveAndFlush(assignment);

        // Access now returns 404
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentsForSupport(supportEngineer.getId(), ticketA.getTicketNumber())
        );
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("Quota enforcement: uploads up to 10 succeed, 11th upload is rejected with 409 Conflict")
    void quotaEnforcementMax10() {
        // Upload 10 attachments
        for (int i = 1; i <= 10; i++) {
            byte[] data = ("File content " + i).getBytes(StandardCharsets.UTF_8);
            TicketAttachmentResponse res = attachmentService.uploadAttachmentForEmployee(
                    employeeA.getId(),
                    ticketA.getTicketNumber(),
                    "file_" + i + ".txt",
                    "text/plain",
                    new ByteArrayInputStream(data),
                    data.length
            );
            createdStorageKeys.add(attachmentRepository.findById(res.id()).get().getStorageKey());
        }

        // 11th upload must be rejected with 409
        byte[] data11 = "Exceeds quota".getBytes(StandardCharsets.UTF_8);
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.uploadAttachmentForEmployee(
                        employeeA.getId(),
                        ticketA.getTicketNumber(),
                        "file_11.txt",
                        "text/plain",
                        new ByteArrayInputStream(data11),
                        data11.length
                )
        );
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("quota exceeded"));
    }

    @Test
    @DisplayName("PENDING records are not visible to normal listing queries")
    void pendingRecordsAreNotVisible() {
        // Create an attachment with PENDING status directly in repository
        TicketAttachment pending = new TicketAttachment(
                ticketA,
                employeeA,
                "pending.txt",
                "pending.txt",
                "text/plain",
                100,
                UUID.randomUUID() + ".dat",
                "0".repeat(64),
                AttachmentStatus.PENDING
        );
        attachmentRepository.saveAndFlush(pending);
        createdStorageKeys.add(pending.getStorageKey());

        List<TicketAttachmentResponse> employeeList = attachmentService.getAttachmentsForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber()
        );
        assertTrue(employeeList.isEmpty(), "PENDING attachment must not appear in normal list");
    }

    @Test
    @DisplayName("Resolved ticket: historical resolving engineer retains read-only access (listing and download)")
    void resolvingSupportEngineerRetainsListingAndDownloadAccessAfterResolution() {
        // 1. Assign ticket to support engineer
        ticketA.markAssigned();
        ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        TicketAssignment assignment = new TicketAssignment(ticketA, supportEngineer);
        ticketAssignmentRepository.saveAndFlush(assignment);

        // 2. Employee uploads PNG
        byte[] pngContent = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52};
        TicketAttachmentResponse empUploaded = attachmentService.uploadAttachmentForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber(),
                "screenshot.png",
                "image/png",
                new ByteArrayInputStream(pngContent),
                pngContent.length
        );
        createdStorageKeys.add(attachmentRepository.findById(empUploaded.id()).get().getStorageKey());

        // 3. Support engineer uploads LOG
        byte[] logContent = "Support engineer diagnostic log".getBytes(StandardCharsets.UTF_8);
        TicketAttachmentResponse supportUploaded = attachmentService.uploadAttachmentForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber(),
                "diag.log",
                "text/plain",
                new ByteArrayInputStream(logContent),
                logContent.length
        );
        createdStorageKeys.add(attachmentRepository.findById(supportUploaded.id()).get().getStorageKey());

        // 4. Resolve the ticket (simulates SupportTicketService.resolveTicket)
        ticketA.resolve("Issue diagnosed and resolved successfully.");
        assignment.release();
        ticketRepository.saveAndFlush(ticketA);
        ticketAssignmentRepository.saveAndFlush(assignment);

        // 5. Support engineer can list both attachments after resolution
        List<TicketAttachmentResponse> supportList = attachmentService.getAttachmentsForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber()
        );
        assertEquals(2, supportList.size());

        // 6. Support engineer can download both attachments after resolution
        AttachmentDownloadDescriptor dl1 = attachmentService.getAttachmentDownloadForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber(),
                empUploaded.id()
        );
        assertNotNull(dl1);
        assertEquals("screenshot.png", dl1.filename());

        AttachmentDownloadDescriptor dl2 = attachmentService.getAttachmentDownloadForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber(),
                supportUploaded.id()
        );
        assertNotNull(dl2);
        assertEquals("diag.log", dl2.filename());

        // 7. Employee can also list and download after resolution
        List<TicketAttachmentResponse> empList = attachmentService.getAttachmentsForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber()
        );
        assertEquals(2, empList.size());

        AttachmentDownloadDescriptor empDl = attachmentService.getAttachmentDownloadForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber(),
                supportUploaded.id()
        );
        assertNotNull(empDl);
        assertEquals("diag.log", empDl.filename());

        // 8. New uploads must be rejected with 409 Conflict for both support and employee
        byte[] extra = "Forbidden upload".getBytes(StandardCharsets.UTF_8);
        ResponseStatusException supportUploadEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.uploadAttachmentForSupport(
                        supportEngineer.getId(),
                        ticketA.getTicketNumber(),
                        "late.txt",
                        "text/plain",
                        new ByteArrayInputStream(extra),
                        extra.length
                )
        );
        assertEquals(HttpStatus.CONFLICT, supportUploadEx.getStatusCode());

        ResponseStatusException empUploadEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.uploadAttachmentForEmployee(
                        employeeA.getId(),
                        ticketA.getTicketNumber(),
                        "late2.txt",
                        "text/plain",
                        new ByteArrayInputStream(extra),
                        extra.length
                )
        );
        assertEquals(HttpStatus.CONFLICT, empUploadEx.getStatusCode());
    }

    @Test
    @DisplayName("Resolved ticket: unauthorized support engineer is denied access (404 Not Found)")
    void unauthorizedSupportEngineerDeniedAccessToResolvedTicket() {
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));
        User otherSupportEngineer = createUser("other-eng", supportRole);

        // Assign to supportEngineer and resolve
        ticketA.markAssigned();
        ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        TicketAssignment assignment = new TicketAssignment(ticketA, supportEngineer);
        ticketAssignmentRepository.saveAndFlush(assignment);

        byte[] content = "Data content".getBytes(StandardCharsets.UTF_8);
        TicketAttachmentResponse uploaded = attachmentService.uploadAttachmentForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber(),
                "data.txt",
                "text/plain",
                new ByteArrayInputStream(content),
                content.length
        );
        createdStorageKeys.add(attachmentRepository.findById(uploaded.id()).get().getStorageKey());

        ticketA.resolve("Resolution summary");
        assignment.release();
        ticketRepository.saveAndFlush(ticketA);
        ticketAssignmentRepository.saveAndFlush(assignment);

        // Different support engineer listing -> 404
        ResponseStatusException listEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentsForSupport(otherSupportEngineer.getId(), ticketA.getTicketNumber())
        );
        assertEquals(HttpStatus.NOT_FOUND, listEx.getStatusCode());

        // Different support engineer downloading -> 404
        ResponseStatusException dlEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentDownloadForSupport(otherSupportEngineer.getId(), ticketA.getTicketNumber(), uploaded.id())
        );
        assertEquals(HttpStatus.NOT_FOUND, dlEx.getStatusCode());
    }

    @Test
    @DisplayName("Resolved ticket: previously revoked engineer on ticket is denied access (404 Not Found)")
    void previouslyRevokedEngineerDeniedAccessToResolvedTicket() throws Exception {
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));
        User revokedEngineer = createUser("revoked-eng", supportRole);

        // 1. First engineer assigned while active
        ticketA.markAssigned();
        ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        TicketAssignment revokedAssignment = new TicketAssignment(ticketA, revokedEngineer);
        ticketAssignmentRepository.saveAndFlush(revokedAssignment);

        byte[] content = "Log content".getBytes(StandardCharsets.UTF_8);
        TicketAttachmentResponse uploaded = attachmentService.uploadAttachmentForEmployee(
                employeeA.getId(),
                ticketA.getTicketNumber(),
                "initial.log",
                "text/plain",
                new ByteArrayInputStream(content),
                content.length
        );
        createdStorageKeys.add(attachmentRepository.findById(uploaded.id()).get().getStorageKey());

        // 2. First engineer's assignment is revoked/released while active
        revokedAssignment.release();
        ticketAssignmentRepository.saveAndFlush(revokedAssignment);

        // Brief delay to ensure distinct timestamp
        Thread.sleep(50);

        // 3. Second engineer assigned and resolves ticket
        TicketAssignment resolvingAssignment = new TicketAssignment(ticketA, supportEngineer);
        ticketAssignmentRepository.saveAndFlush(resolvingAssignment);

        ticketA.resolve("Resolved by second engineer");
        resolvingAssignment.release();
        ticketRepository.saveAndFlush(ticketA);
        ticketAssignmentRepository.saveAndFlush(resolvingAssignment);

        // 4. Revoked engineer listing on resolved ticket -> 404 Not Found
        ResponseStatusException listEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentsForSupport(revokedEngineer.getId(), ticketA.getTicketNumber())
        );
        assertEquals(HttpStatus.NOT_FOUND, listEx.getStatusCode());

        // 5. Revoked engineer downloading on resolved ticket -> 404 Not Found
        ResponseStatusException dlEx = assertThrows(
                ResponseStatusException.class,
                () -> attachmentService.getAttachmentDownloadForSupport(revokedEngineer.getId(), ticketA.getTicketNumber(), uploaded.id())
        );
        assertEquals(HttpStatus.NOT_FOUND, dlEx.getStatusCode());

        // 6. Legitimate resolving engineer can list and download
        List<TicketAttachmentResponse> resolvingList = attachmentService.getAttachmentsForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber()
        );
        assertEquals(1, resolvingList.size());

        AttachmentDownloadDescriptor dl = attachmentService.getAttachmentDownloadForSupport(
                supportEngineer.getId(),
                ticketA.getTicketNumber(),
                uploaded.id()
        );
        assertNotNull(dl);
        assertEquals("initial.log", dl.filename());
    }

    private User createUser(String prefix, Role role) {
        UUID id = UUID.randomUUID();
        User user = new User(
                prefix + "-" + id + "@flowdesk-test.invalid",
                "hashedpassword",
                prefix.toUpperCase(),
                "TestUser"
        );
        user.getRoles().add(role);
        return userRepository.saveAndFlush(user);
    }

    private Ticket createTicket(User creator, String ticketNumber, String title) {
        Ticket ticket = new Ticket(
                ticketNumber,
                TicketType.INCIDENT,
                title,
                "Integration test ticket description",
                creator
        );
        return ticketRepository.saveAndFlush(ticket);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
