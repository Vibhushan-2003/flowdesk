package com.flowdesk.attachment.service;

import com.flowdesk.attachment.domain.AttachmentStatus;
import com.flowdesk.attachment.domain.TicketAttachment;
import com.flowdesk.attachment.dto.AttachmentDownloadDescriptor;
import com.flowdesk.attachment.dto.TicketAttachmentResponse;
import com.flowdesk.attachment.repository.TicketAttachmentRepository;
import com.flowdesk.attachment.storage.AttachmentStorageProperties;
import com.flowdesk.attachment.storage.AttachmentStorageService;
import com.flowdesk.attachment.storage.StoredFile;
import com.flowdesk.attachment.validation.AllowedAttachmentType;
import com.flowdesk.attachment.validation.FileContentValidator;
import com.flowdesk.attachment.validation.FileValidationException;
import com.flowdesk.attachment.validation.FileValidationResult;
import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TicketAttachmentService Unit Tests")
class TicketAttachmentServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketAttachmentRepository attachmentRepository;

    @Mock
    private AttachmentStorageService storageService;

    @Mock
    private FileContentValidator fileContentValidator;

    @Mock
    private AuditService auditService;

    private AttachmentStorageProperties properties;
    private TicketAttachmentService service;

    private User employeeA;
    private User employeeB;
    private User supportEngineer;
    private Ticket ticketA;

    @BeforeEach
    void setUp() throws Exception {
        properties = new AttachmentStorageProperties();
        properties.setMaxAttachmentsPerTicket(10);
        properties.setMaxFileSizeBytes(10 * 1024 * 1024L);

        service = new TicketAttachmentService(
                ticketRepository,
                ticketAssignmentRepository,
                userRepository,
                attachmentRepository,
                storageService,
                fileContentValidator,
                properties,
                auditService
        );

        employeeA = new User("alice@flowdesk.test", "hash", "Alice", "Smith");
        setField(employeeA, "id", UUID.randomUUID());

        employeeB = new User("bob@flowdesk.test", "hash", "Bob", "Jones");
        setField(employeeB, "id", UUID.randomUUID());

        supportEngineer = new User("charlie@flowdesk.test", "hash", "Charlie", "Engineer");
        setField(supportEngineer, "id", UUID.randomUUID());

        ticketA = new Ticket(
                "FD-000101",
                TicketType.INCIDENT,
                "VPN issue",
                "VPN is disconnecting frequently",
                employeeA
        );
        setField(ticketA, "id", UUID.randomUUID());
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("Employee Upload Tests")
    class EmployeeUploadTests {

        @Test
        @DisplayName("Should successfully upload attachment for ticket creator")
        void shouldUploadAttachmentForEmployee() {
            String originalFilename = "vpn_log.txt";
            String declaredContentType = "text/plain";
            byte[] content = "Log line 1".getBytes(StandardCharsets.UTF_8);
            String storageKey = "11111111-2222-3333-4444-555555555555.dat";
            String sha256 = "a".repeat(64);

            when(userRepository.findById(employeeA.getId())).thenReturn(Optional.of(employeeA));
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeA.getId()))
                    .thenReturn(Optional.of(ticketA));
            when(ticketRepository.findByTicketNumberForUpdate("FD-000101"))
                    .thenReturn(Optional.of(ticketA));

            when(storageService.store(any(InputStream.class), eq((long) content.length)))
                    .thenReturn(new StoredFile(storageKey, content.length, sha256));
            when(storageService.load(storageKey))
                    .thenReturn(new ByteArrayInputStream(content));

            when(fileContentValidator.validate(eq(originalFilename), eq(declaredContentType), any(InputStream.class), eq((long) content.length)))
                    .thenReturn(new FileValidationResult("vpn_log.txt", AllowedAttachmentType.TXT, "text/plain", "txt", content.length, sha256));

            when(attachmentRepository.countByTicket_IdAndStatusIn(eq(ticketA.getId()), anyCollection()))
                    .thenReturn(2L);

            TicketAttachment saved = new TicketAttachment(
                    ticketA, employeeA, originalFilename, "vpn_log.txt", "text/plain", content.length, storageKey, sha256, AttachmentStatus.AVAILABLE
            );
            when(attachmentRepository.saveAndFlush(any(TicketAttachment.class))).thenReturn(saved);

            TicketAttachmentResponse response = service.uploadAttachmentForEmployee(
                    employeeA.getId(),
                    "FD-000101",
                    originalFilename,
                    declaredContentType,
                    new ByteArrayInputStream(content),
                    content.length
            );

            assertNotNull(response);
            assertEquals("FD-000101", response.ticketNumber());
            assertEquals("vpn_log.txt", response.filename());
            assertEquals("text/plain", response.contentType());
            assertEquals(content.length, response.sizeBytes());
            assertEquals(employeeA.getId(), response.uploadedByUserId());

            verify(auditService).recordUserAction(
                    eq(employeeA),
                    eq(AuditAction.TICKET_ATTACHMENT_UPLOADED),
                    any(),
                    eq(ticketA.getId()),
                    eq("FD-000101"),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should reject upload when employee does not own ticket (404 Not Found)")
        void shouldRejectUploadWhenEmployeeDoesNotOwnTicket() {
            when(userRepository.findById(employeeB.getId())).thenReturn(Optional.of(employeeB));
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeB.getId()))
                    .thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.uploadAttachmentForEmployee(
                            employeeB.getId(),
                            "FD-000101",
                            "file.pdf",
                            "application/pdf",
                            new ByteArrayInputStream("data".getBytes()),
                            4
                    )
            );
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
            verify(storageService, never()).store(any(), anyLong());
        }

        @Test
        @DisplayName("Should reject upload to finished ticket (409 Conflict)")
        void shouldRejectUploadToFinishedTicket() {
            ticketA.markAssigned();
            ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
            ticketA.resolve("Issue fixed");

            when(userRepository.findById(employeeA.getId())).thenReturn(Optional.of(employeeA));
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeA.getId()))
                    .thenReturn(Optional.of(ticketA));

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.uploadAttachmentForEmployee(
                            employeeA.getId(),
                            "FD-000101",
                            "file.pdf",
                            "application/pdf",
                            new ByteArrayInputStream("data".getBytes()),
                            4
                    )
            );
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            verify(storageService, never()).store(any(), anyLong());
        }

        @Test
        @DisplayName("Should clean up storage and reject when quota is exceeded (409 Conflict)")
        void shouldCleanUpAndRejectWhenQuotaExceeded() {
            String storageKey = "99999999-9999-9999-9999-999999999999.dat";
            byte[] content = "content".getBytes();

            when(userRepository.findById(employeeA.getId())).thenReturn(Optional.of(employeeA));
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeA.getId()))
                    .thenReturn(Optional.of(ticketA));
            when(ticketRepository.findByTicketNumberForUpdate("FD-000101"))
                    .thenReturn(Optional.of(ticketA));

            when(storageService.store(any(), anyLong()))
                    .thenReturn(new StoredFile(storageKey, content.length, "b".repeat(64)));
            when(storageService.load(storageKey))
                    .thenReturn(new ByteArrayInputStream(content));
            when(fileContentValidator.validate(any(), any(), any(), anyLong()))
                    .thenReturn(new FileValidationResult("f.txt", AllowedAttachmentType.TXT, "text/plain", "txt", content.length, "b".repeat(64)));

            // 10 active attachments already exist
            when(attachmentRepository.countByTicket_IdAndStatusIn(eq(ticketA.getId()), anyCollection()))
                    .thenReturn(10L);

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.uploadAttachmentForEmployee(
                            employeeA.getId(),
                            "FD-000101",
                            "f.txt",
                            "text/plain",
                            new ByteArrayInputStream(content),
                            content.length
                    )
            );
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("quota exceeded"));
            verify(storageService).delete(storageKey);
            verify(attachmentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should delete stored file when validation fails")
        void shouldDeleteStoredFileWhenValidationFails() {
            String storageKey = "22222222-2222-2222-2222-222222222222.dat";
            byte[] content = new byte[]{0x4D, 0x5A}; // Executable

            when(userRepository.findById(employeeA.getId())).thenReturn(Optional.of(employeeA));
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeA.getId()))
                    .thenReturn(Optional.of(ticketA));

            when(storageService.store(any(), anyLong()))
                    .thenReturn(new StoredFile(storageKey, content.length, "c".repeat(64)));
            when(storageService.load(storageKey))
                    .thenReturn(new ByteArrayInputStream(content));

            when(fileContentValidator.validate(any(), any(), any(), anyLong()))
                    .thenThrow(new FileValidationException("Executable Windows PE binary disguised as text file"));

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.uploadAttachmentForEmployee(
                            employeeA.getId(),
                            "FD-000101",
                            "test.txt",
                            "text/plain",
                            new ByteArrayInputStream(content),
                            content.length
                    )
            );
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(storageService).delete(storageKey);
        }
    }

    @Nested
    @DisplayName("Support Engineer Authorization Tests")
    class SupportAuthorizationTests {

        @Test
        @DisplayName("Should allow upload for actively assigned support engineer")
        void shouldAllowUploadForAssignedEngineer() {
            TicketAssignment assignment = new TicketAssignment(ticketA, supportEngineer);

            when(userRepository.findById(supportEngineer.getId())).thenReturn(Optional.of(supportEngineer));
            when(ticketAssignmentRepository.findAuthorizedSupportAssignments("FD-000101", supportEngineer.getId()))
                    .thenReturn(List.of(assignment));
            when(ticketRepository.findByTicketNumberForUpdate("FD-000101"))
                    .thenReturn(Optional.of(ticketA));

            String storageKey = "33333333-3333-3333-3333-333333333333.dat";
            byte[] content = "patch notes".getBytes();
            String sha256 = "d".repeat(64);

            when(storageService.store(any(), anyLong()))
                    .thenReturn(new StoredFile(storageKey, content.length, sha256));
            when(storageService.load(storageKey))
                    .thenReturn(new ByteArrayInputStream(content));
            when(fileContentValidator.validate(any(), any(), any(), anyLong()))
                    .thenReturn(new FileValidationResult("patch.txt", AllowedAttachmentType.TXT, "text/plain", "txt", content.length, sha256));
            when(attachmentRepository.countByTicket_IdAndStatusIn(any(), anyCollection()))
                    .thenReturn(0L);

            TicketAttachment saved = new TicketAttachment(
                    ticketA, supportEngineer, "patch.txt", "patch.txt", "text/plain", content.length, storageKey, sha256, AttachmentStatus.AVAILABLE
            );
            when(attachmentRepository.saveAndFlush(any())).thenReturn(saved);

            TicketAttachmentResponse response = service.uploadAttachmentForSupport(
                    supportEngineer.getId(),
                    "FD-000101",
                    "patch.txt",
                    "text/plain",
                    new ByteArrayInputStream(content),
                    content.length
            );

            assertNotNull(response);
            assertEquals("patch.txt", response.filename());
            verify(auditService).recordUserAction(eq(supportEngineer), eq(AuditAction.TICKET_ATTACHMENT_UPLOADED), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Should reject support engineer access if ticket is not authorized (404 Not Found)")
        void shouldRejectUnassignedSupportEngineer() {
            when(ticketAssignmentRepository.findAuthorizedSupportAssignments("FD-000101", supportEngineer.getId()))
                    .thenReturn(List.of());

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.getAttachmentsForSupport(supportEngineer.getId(), "FD-000101")
            );
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("Should allow historical resolving engineer to list and download attachments on resolved ticket")
        void shouldAllowResolvingEngineerAccessOnResolvedTicket() throws Exception {
            ticketA.markAssigned();
            ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
            ticketA.resolve("Issue fixed");

            TicketAssignment resolvingAssignment = new TicketAssignment(ticketA, supportEngineer);
            resolvingAssignment.release();

            UUID attachmentId = UUID.randomUUID();
            TicketAttachment attachment = new TicketAttachment(
                    ticketA, employeeA, "evidence.png", "evidence.png", "image/png", 3033, "storage_key.dat", "a".repeat(64), AttachmentStatus.AVAILABLE
            );
            setField(attachment, "id", attachmentId);

            when(userRepository.findById(supportEngineer.getId())).thenReturn(Optional.of(supportEngineer));
            when(ticketAssignmentRepository.findAuthorizedSupportAssignments("FD-000101", supportEngineer.getId()))
                    .thenReturn(List.of(resolvingAssignment));
            when(attachmentRepository.findByTicket_IdAndStatusOrderByCreatedAtAsc(ticketA.getId(), AttachmentStatus.AVAILABLE))
                    .thenReturn(List.of(attachment));
            when(attachmentRepository.findByTicket_TicketNumberAndIdAndStatus("FD-000101", attachmentId, AttachmentStatus.AVAILABLE))
                    .thenReturn(Optional.of(attachment));
            when(storageService.exists("storage_key.dat")).thenReturn(true);

            // Test listing
            List<TicketAttachmentResponse> listed = service.getAttachmentsForSupport(supportEngineer.getId(), "FD-000101");
            assertEquals(1, listed.size());
            assertEquals("evidence.png", listed.get(0).filename());

            // Test download
            AttachmentDownloadDescriptor descriptor = service.getAttachmentDownloadForSupport(supportEngineer.getId(), "FD-000101", attachmentId);
            assertNotNull(descriptor);
            assertEquals("evidence.png", descriptor.filename());
            verify(auditService).recordUserAction(eq(supportEngineer), eq(AuditAction.TICKET_ATTACHMENT_DOWNLOADED), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Should forbid upload on resolved ticket for support engineer (409 Conflict)")
        void shouldForbidUploadOnResolvedTicketForSupportEngineer() {
            ticketA.markAssigned();
            ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
            ticketA.resolve("Completed");

            TicketAssignment resolvingAssignment = new TicketAssignment(ticketA, supportEngineer);
            resolvingAssignment.release();

            when(userRepository.findById(supportEngineer.getId())).thenReturn(Optional.of(supportEngineer));
            when(ticketAssignmentRepository.findAuthorizedSupportAssignments("FD-000101", supportEngineer.getId()))
                    .thenReturn(List.of(resolvingAssignment));

            byte[] content = "extra log".getBytes();
            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.uploadAttachmentForSupport(
                            supportEngineer.getId(),
                            "FD-000101",
                            "extra.log",
                            "text/plain",
                            new ByteArrayInputStream(content),
                            content.length
                    )
            );
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            assertTrue(ex.getMessage().contains("finished ticket"));
        }

        @Test
        @DisplayName("Should reject unauthorized support engineer on resolved ticket (404 Not Found)")
        void shouldRejectUnauthorizedEngineerOnResolvedTicket() {
            ticketA.markAssigned();
            ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
            ticketA.resolve("Completed");

            UUID unauthorizedEngineerId = UUID.randomUUID();
            when(ticketAssignmentRepository.findAuthorizedSupportAssignments("FD-000101", unauthorizedEngineerId))
                    .thenReturn(List.of());

            ResponseStatusException listEx = assertThrows(
                    ResponseStatusException.class,
                    () -> service.getAttachmentsForSupport(unauthorizedEngineerId, "FD-000101")
            );
            assertEquals(HttpStatus.NOT_FOUND, listEx.getStatusCode());
        }

        @Test
        @DisplayName("Should reject released engineer on active ticket (404 Not Found)")
        void shouldRejectReleasedEngineerOnActiveTicket() {
            // Active ticket, but support engineer has released assignment -> repository returns empty
            when(ticketAssignmentRepository.findAuthorizedSupportAssignments("FD-000101", supportEngineer.getId()))
                    .thenReturn(List.of());

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.getAttachmentsForSupport(supportEngineer.getId(), "FD-000101")
            );
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    @Nested
    @DisplayName("Listing and Download Tests")
    class ListingAndDownloadTests {

        @Test
        @DisplayName("Should only return AVAILABLE attachments when listing")
        void shouldOnlyListAvailableAttachments() {
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeA.getId()))
                    .thenReturn(Optional.of(ticketA));

            TicketAttachment active = new TicketAttachment(
                    ticketA, employeeA, "doc.pdf", "doc.pdf", "application/pdf", 1024, "key1.dat", "e".repeat(64), AttachmentStatus.AVAILABLE
            );
            when(attachmentRepository.findByTicket_IdAndStatusOrderByCreatedAtAsc(ticketA.getId(), AttachmentStatus.AVAILABLE))
                    .thenReturn(List.of(active));

            List<TicketAttachmentResponse> responses = service.getAttachmentsForEmployee(employeeA.getId(), "FD-000101");

            assertEquals(1, responses.size());
            assertEquals("doc.pdf", responses.get(0).filename());
        }

        @Test
        @DisplayName("Should allow downloading AVAILABLE attachment on finished tickets")
        void shouldAllowDownloadOnFinishedTicket() throws Exception {
            ticketA.markAssigned();
            ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
            ticketA.resolve("Resolved");

            UUID attachmentId = UUID.randomUUID();
            TicketAttachment attachment = new TicketAttachment(
                    ticketA, employeeA, "final.pdf", "final.pdf", "application/pdf", 2048, "key2.dat", "f".repeat(64), AttachmentStatus.AVAILABLE
            );
            setField(attachment, "id", attachmentId);

            when(userRepository.findById(employeeA.getId())).thenReturn(Optional.of(employeeA));
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeA.getId()))
                    .thenReturn(Optional.of(ticketA));
            when(attachmentRepository.findByTicket_TicketNumberAndIdAndStatus("FD-000101", attachmentId, AttachmentStatus.AVAILABLE))
                    .thenReturn(Optional.of(attachment));
            when(storageService.exists("key2.dat")).thenReturn(true);

            AttachmentDownloadDescriptor descriptor = service.getAttachmentDownloadForEmployee(employeeA.getId(), "FD-000101", attachmentId);

            assertNotNull(descriptor);
            assertEquals("final.pdf", descriptor.filename());
            assertEquals("key2.dat", descriptor.storageKey());
            verify(auditService).recordUserAction(eq(employeeA), eq(AuditAction.TICKET_ATTACHMENT_DOWNLOADED), any(), any(), any(), anyMap());
        }

        @Test
        @DisplayName("Should reject download if attachment belongs to a different ticket (404 Not Found)")
        void shouldRejectCrossTicketAttachmentDownload() {
            UUID randomAttachmentId = UUID.randomUUID();

            when(userRepository.findById(employeeA.getId())).thenReturn(Optional.of(employeeA));
            when(ticketRepository.findByTicketNumberAndCreatedByUser_Id("FD-000101", employeeA.getId()))
                    .thenReturn(Optional.of(ticketA));
            when(attachmentRepository.findByTicket_TicketNumberAndIdAndStatus("FD-000101", randomAttachmentId, AttachmentStatus.AVAILABLE))
                    .thenReturn(Optional.empty());

            ResponseStatusException ex = assertThrows(
                    ResponseStatusException.class,
                    () -> service.getAttachmentDownloadForEmployee(employeeA.getId(), "FD-000101", randomAttachmentId)
            );
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }
}
