package com.flowdesk.attachment.controller;

import com.flowdesk.attachment.domain.AttachmentStatus;
import com.flowdesk.attachment.domain.TicketAttachment;
import com.flowdesk.attachment.repository.TicketAttachmentRepository;
import com.flowdesk.attachment.storage.AttachmentStorageService;
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
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketAttachmentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAssignmentRepository assignmentRepository;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AttachmentStorageService storageService;

    private User employeeA;
    private User employeeB;
    private User supportEngineer;
    private User unassignedSupport;
    private Ticket ticketA;
    private Ticket ticketB;
    private final List<String> createdStorageKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));

        employeeA = createUser("emp-api-a", employeeRole);
        employeeB = createUser("emp-api-b", employeeRole);
        supportEngineer = createUser("sup-api-eng", supportRole);
        unassignedSupport = createUser("sup-api-unassigned", supportRole);

        ticketA = createTicket(employeeA, "FD-API-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "API Ticket A");
        ticketB = createTicket(employeeB, "FD-API-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(), "API Ticket B");

        // Assign ticketA to supportEngineer
        ticketA.markAssigned();
        ticketA = ticketRepository.saveAndFlush(ticketA);
        assignmentRepository.saveAndFlush(new TicketAssignment(ticketA, supportEngineer));
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
    @DisplayName("End-to-end Employee upload, list and download via HTTP APIs")
    void employeeUploadListDownloadE2E() throws Exception {
        byte[] pdfContent = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file", "vpn_setup.pdf", "application/pdf", pdfContent
        );

        // 1. Upload
        String responseBody = mockMvc.perform(multipart("/api/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(employeeA.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename", is("vpn_setup.pdf")))
                .andExpect(jsonPath("$.contentType", is("application/pdf")))
                .andExpect(jsonPath("$.sizeBytes", is(pdfContent.length)))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID attachmentId = UUID.fromString(
                com.jayway.jsonpath.JsonPath.read(responseBody, "$.id")
        );

        TicketAttachment entity = attachmentRepository.findById(attachmentId).orElseThrow();
        createdStorageKeys.add(entity.getStorageKey());
        assertTrue(storageService.exists(entity.getStorageKey()));

        // 2. List
        mockMvc.perform(get("/api/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(employeeA.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(attachmentId.toString())))
                .andExpect(jsonPath("$[0].filename", is("vpn_setup.pdf")))
                .andExpect(jsonPath("$[0].storageKey").doesNotExist());

        // 3. Download
        byte[] downloadedBytes = mockMvc.perform(get("/api/tickets/" + ticketA.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(employeeA.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment; filename=\"vpn_setup.pdf\"")))
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(pdfContent, downloadedBytes);
    }

    @Test
    @DisplayName("Employee B denied access to Employee A's ticket (404 Not Found)")
    void employeeCrossUserIsolation() throws Exception {
        byte[] pdfContent = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.pdf", "application/pdf", pdfContent
        );

        mockMvc.perform(multipart("/api/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(employeeB.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Ticket not found")));

        mockMvc.perform(get("/api/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(employeeB.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Support engineer can upload and download on assigned ticket")
    void supportEngineerUploadAndDownload() throws Exception {
        byte[] logContent = "Support diagnostic log line 1\nLine 2".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "diag.txt", "text/plain", logContent
        );

        // Upload as support engineer
        String response = mockMvc.perform(multipart("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename", is("diag.txt")))
                .andExpect(jsonPath("$.storageKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        UUID attachmentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id"));
        TicketAttachment entity = attachmentRepository.findById(attachmentId).orElseThrow();
        createdStorageKeys.add(entity.getStorageKey());

        // Download as support engineer
        mockMvc.perform(get("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(logContent));
    }

    @Test
    @DisplayName("Unassigned support engineer denied access to support endpoints (404 Not Found)")
    void unassignedSupportDenied() throws Exception {
        mockMvc.perform(get("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(unassignedSupport.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Ticket not found")));
    }

    @Test
    @DisplayName("Finished ticket rejects new upload with 409 Conflict")
    void finishedTicketRejectsUpload() throws Exception {
        ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        ticketA.resolve("Issue solved");
        ticketRepository.save(ticketA);

        MockMultipartFile file = new MockMultipartFile(
                "file", "late.txt", "text/plain", "data".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(employeeA.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("finished ticket")));
    }

    @Test
    @DisplayName("Invalid file (executable/magic bytes violation) returns 400 Bad Request")
    void invalidFileReturns400() throws Exception {
        byte[] exeBytes = new byte[]{0x4D, 0x5A, 0x00, 0x00}; // MZ (DOS PE)
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.txt", "text/plain", exeBytes
        );

        mockMvc.perform(multipart("/api/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(employeeA.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Executable Windows PE binary")));
    }

    @Test
    @DisplayName("Resolving support engineer can list and download attachments after ticket resolution")
    void resolvingSupportEngineerCanListAndDownloadAfterResolution() throws Exception {
        byte[] logContent = "Diagnostic log before resolution".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "diag.log", "text/plain", logContent
        );

        // Upload as assigned engineer
        String response = mockMvc.perform(multipart("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID attachmentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(response, "$.id"));
        TicketAttachment entity = attachmentRepository.findById(attachmentId).orElseThrow();
        createdStorageKeys.add(entity.getStorageKey());

        // Resolve ticket and release assignment
        TicketAssignment activeAssignment = assignmentRepository.findByTicket_IdAndReleasedAtIsNull(ticketA.getId()).orElseThrow();
        ticketA.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        ticketA.resolve("Resolution notes");
        activeAssignment.release();
        ticketRepository.saveAndFlush(ticketA);
        assignmentRepository.saveAndFlush(activeAssignment);

        // Resolving engineer can list attachments after resolution
        mockMvc.perform(get("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename", is("diag.log")));

        // Resolving engineer can download attachment after resolution
        mockMvc.perform(get("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(logContent));

        // Unauthorized support engineer is rejected (404)
        mockMvc.perform(get("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(unassignedSupport.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", is("Ticket not found")));

        // Support engineer upload after resolution is rejected with 409 Conflict
        MockMultipartFile lateFile = new MockMultipartFile(
                "file", "post_resolve.txt", "text/plain", "late data".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/support/tickets/" + ticketA.getTicketNumber() + "/attachments")
                        .file(lateFile)
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("finished ticket")));
    }
}
