package com.flowdesk.ticket.controller;

import com.flowdesk.attachment.domain.TicketAttachment;
import com.flowdesk.attachment.repository.TicketAttachmentRepository;
import com.flowdesk.attachment.storage.AttachmentStorageService;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportTicketResolvedAccessIntegrationTest {

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

    private User employee;
    private User otherEmployee;
    private User resolvingSupportEngineer;
    private User releasedSupportEngineer;
    private User unassignedSupportEngineer;

    private Ticket resolvedTicket;
    private Ticket activeTicket;
    private UUID attachmentId;
    private final byte[] logContent = "Diagnostic log trace 123".getBytes(StandardCharsets.UTF_8);
    private final List<String> createdStorageKeys = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));

        employee = createUser("emp-res", employeeRole);
        otherEmployee = createUser("emp-other", employeeRole);
        resolvingSupportEngineer = createUser("sup-resolving", supportRole);
        releasedSupportEngineer = createUser("sup-released", supportRole);
        unassignedSupportEngineer = createUser("sup-never", supportRole);

        // 1. Create resolvedTicket
        String resolvedTicketNum = "FD-RES-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        resolvedTicket = createTicket(employee, resolvedTicketNum, "Resolved Test Ticket");

        // First assignment: releasedSupportEngineer was assigned, then released prior to resolution
        resolvedTicket.markAssigned();
        resolvedTicket = ticketRepository.saveAndFlush(resolvedTicket);
        TicketAssignment releasedAssignment = new TicketAssignment(resolvedTicket, releasedSupportEngineer);
        releasedAssignment = assignmentRepository.saveAndFlush(releasedAssignment);
        releasedAssignment.release();
        assignmentRepository.saveAndFlush(releasedAssignment);

        // Second assignment: resolvingSupportEngineer assigned, uploads attachment, transitions to IN_PROGRESS, resolves ticket
        TicketAssignment resolvingAssignment = new TicketAssignment(resolvedTicket, resolvingSupportEngineer);
        resolvingAssignment = assignmentRepository.saveAndFlush(resolvingAssignment);

        MockMultipartFile file = new MockMultipartFile(
                "file", "diagnostic.log", "text/plain", logContent
        );
        String uploadResponse = mockMvc.perform(multipart("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        attachmentId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(uploadResponse, "$.id"));
        TicketAttachment attachmentEntity = attachmentRepository.findById(attachmentId).orElseThrow();
        createdStorageKeys.add(attachmentEntity.getStorageKey());

        // Resolve ticket and release assignment
        resolvedTicket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        resolvedTicket.resolve("Resolved diagnostic issue successfully.");
        resolvedTicket = ticketRepository.saveAndFlush(resolvedTicket);

        resolvingAssignment.release();
        assignmentRepository.saveAndFlush(resolvingAssignment);

        // 2. Create activeTicket (assigned to resolvingSupportEngineer, not resolved)
        String activeTicketNum = "FD-ACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        activeTicket = createTicket(employee, activeTicketNum, "Active Test Ticket");
        activeTicket.markAssigned();
        activeTicket = ticketRepository.saveAndFlush(activeTicket);
        TicketAssignment activeAssignment = new TicketAssignment(activeTicket, resolvingSupportEngineer);
        assignmentRepository.saveAndFlush(activeAssignment);
    }

    private User createUser(String prefix, Role role) {
        UUID id = UUID.randomUUID();
        User user = new User(
                prefix + "-" + id + "@flowdesk-test.invalid",
                "hashedpassword",
                prefix.toUpperCase(),
                "Engineer"
        );
        user.getRoles().add(role);
        return userRepository.saveAndFlush(user);
    }

    private Ticket createTicket(User creator, String ticketNumber, String title) {
        Ticket ticket = new Ticket(
                ticketNumber,
                TicketType.INCIDENT,
                title,
                "Support test ticket description",
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
    @DisplayName("1. Resolving engineer can GET resolved ticket details")
    void resolvingEngineerCanGetResolvedTicketDetails() throws Exception {
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber())
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNumber", is(resolvedTicket.getTicketNumber())))
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolutionSummary", is("Resolved diagnostic issue successfully.")));
    }

    @Test
    @DisplayName("2. Resolving engineer can read comments, list and download attachments on resolved ticket")
    void resolvingEngineerCanReadCommentsListAndDownloadAttachments() throws Exception {
        // Comments
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk());

        // Attachments listing
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].filename", is("diagnostic.log")));

        // Attachment download
        byte[] downloaded = mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("diagnostic.log")))
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(logContent, downloaded);
    }

    @Test
    @DisplayName("3. Previously released engineer cannot access resolved ticket")
    void releasedEngineerCannotAccessResolvedTicket() throws Exception {
        // Details -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber())
                        .with(jwt().jwt(j -> j.subject(releasedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        // Comments -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(releasedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        // Attachments -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(releasedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        // Download -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(releasedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("4. Never-assigned engineer cannot access the ticket")
    void neverAssignedEngineerCannotAccessTicket() throws Exception {
        // Details -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber())
                        .with(jwt().jwt(j -> j.subject(unassignedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        // Comments -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(unassignedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        // Attachments -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(unassignedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        // Download -> 404
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(unassignedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("5. Active ticket assigned to another engineer remains inaccessible")
    void activeTicketAssignedToAnotherEngineerIsInaccessible() throws Exception {
        // releasedSupportEngineer is NOT assigned to activeTicket -> 404
        mockMvc.perform(get("/api/support/tickets/" + activeTicket.getTicketNumber())
                        .with(jwt().jwt(j -> j.subject(releasedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        // unassignedSupportEngineer is NOT assigned to activeTicket -> 404
        mockMvc.perform(get("/api/support/tickets/" + activeTicket.getTicketNumber())
                        .with(jwt().jwt(j -> j.subject(unassignedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("6. Resolved ticket cannot accept new mutations")
    void resolvedTicketCannotAcceptNewMutations() throws Exception {
        // Status update -> 404 (assignment is released)
        mockMvc.perform(patch("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/status")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"WAITING_FOR_USER\"}"))
                .andExpect(status().isNotFound());

        // Resolve again -> 404 (assignment is released)
        mockMvc.perform(patch("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/resolve")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionSummary\": \"Another resolution summary attempt.\"}"))
                .andExpect(status().isNotFound());

        // Add comment -> 404 (assignment is released)
        mockMvc.perform(post("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"New support comment on resolved ticket\"}"))
                .andExpect(status().isNotFound());

        // Upload attachment -> 409 CONFLICT (ticket is finished)
        MockMultipartFile file = new MockMultipartFile(
                "file", "new.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments")
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("7. Employee ticket access remains unchanged")
    void employeeTicketAccessRemainsUnchanged() throws Exception {
        // Ticket creator can view ticket details
        mockMvc.perform(get("/api/tickets/" + resolvedTicket.getTicketNumber())
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNumber", is(resolvedTicket.getTicketNumber())))
                .andExpect(jsonPath("$.status", is("RESOLVED")));

        // Ticket creator can view comments
        mockMvc.perform(get("/api/tickets/" + resolvedTicket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());

        // Ticket creator can view attachments
        mockMvc.perform(get("/api/tickets/" + resolvedTicket.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // Ticket creator can download attachment
        mockMvc.perform(get("/api/tickets/" + resolvedTicket.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());

        // Unrelated employee cannot access resolvedTicket attachments
        mockMvc.perform(get("/api/tickets/" + resolvedTicket.getTicketNumber() + "/attachments")
                        .with(jwt().jwt(j -> j.subject(otherEmployee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isNotFound());
    }
}
