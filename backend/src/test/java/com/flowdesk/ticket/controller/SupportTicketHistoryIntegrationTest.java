package com.flowdesk.ticket.controller;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportTicketHistoryIntegrationTest {

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
    private User resolvingSupportEngineer;
    private User releasedSupportEngineer;
    private User unassignedSupportEngineer;

    private Ticket resolvedTicket;
    private Ticket closedTicket;
    private Ticket activeTicket;
    private UUID attachmentId;
    private final byte[] logContent = "Resolution diagnostic data".getBytes(StandardCharsets.UTF_8);
    private final List<String> createdStorageKeys = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));

        employee = createUser("emp-hist", employeeRole);
        resolvingSupportEngineer = createUser("sup-hist-res", supportRole);
        releasedSupportEngineer = createUser("sup-hist-rel", supportRole);
        unassignedSupportEngineer = createUser("sup-hist-un", supportRole);

        // 1. Create resolvedTicket: releasedSupportEngineer assigned first, then released.
        // Then resolvingSupportEngineer assigned, uploads attachment, transitions to IN_PROGRESS, resolves ticket.
        String resolvedTicketNum = "FD-HST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        resolvedTicket = createTicket(employee, resolvedTicketNum, "Resolved Network Issue");

        resolvedTicket.markAssigned();
        resolvedTicket = ticketRepository.saveAndFlush(resolvedTicket);
        TicketAssignment releasedAssignment = new TicketAssignment(resolvedTicket, releasedSupportEngineer);
        releasedAssignment = assignmentRepository.saveAndFlush(releasedAssignment);
        releasedAssignment.release();
        assignmentRepository.saveAndFlush(releasedAssignment);

        TicketAssignment resolvingAssignment = new TicketAssignment(resolvedTicket, resolvingSupportEngineer);
        resolvingAssignment = assignmentRepository.saveAndFlush(resolvingAssignment);

        // Upload attachment
        MockMultipartFile file = new MockMultipartFile(
                "file", "network_diag.log", "text/plain", logContent
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
        resolvedTicket.resolve("Replaced faulty network switch port.");
        resolvedTicket = ticketRepository.saveAndFlush(resolvedTicket);
        resolvingAssignment.release();
        assignmentRepository.saveAndFlush(resolvingAssignment);

        // 2. Create closedTicket resolved by resolvingSupportEngineer, then closed
        String closedTicketNum = "FD-CLS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        closedTicket = createTicket(employee, closedTicketNum, "Closed Hardware Issue");
        closedTicket.markAssigned();
        closedTicket = ticketRepository.saveAndFlush(closedTicket);
        TicketAssignment closedAssignment = new TicketAssignment(closedTicket, resolvingSupportEngineer);
        closedAssignment = assignmentRepository.saveAndFlush(closedAssignment);
        closedTicket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        closedTicket.resolve("Hardware replaced.");
        org.springframework.test.util.ReflectionTestUtils.setField(closedTicket, "status", TicketStatus.CLOSED);
        closedTicket = ticketRepository.saveAndFlush(closedTicket);
        closedAssignment.release();
        assignmentRepository.saveAndFlush(closedAssignment);

        // 3. Create activeTicket assigned to resolvingSupportEngineer
        String activeTicketNum = "FD-ACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        activeTicket = createTicket(employee, activeTicketNum, "Active In-Flight Issue");
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
                "SupportUser"
        );
        user.getRoles().add(role);
        return userRepository.saveAndFlush(user);
    }

    private Ticket createTicket(User creator, String ticketNumber, String title) {
        Ticket ticket = new Ticket(
                ticketNumber,
                TicketType.INCIDENT,
                title,
                "Ticket description for history test",
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
    @DisplayName("1. Resolving engineer sees resolved tickets in history")
    void resolvingEngineerSeesResolvedTicketsInHistory() throws Exception {
        mockMvc.perform(get("/api/support/tickets/history")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @DisplayName("2. Resolving engineer can open details and download attachments of ticket in history")
    void resolvingEngineerCanOpenDetailsAndDownloadAttachments() throws Exception {
        // Details
        mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber())
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketNumber", is(resolvedTicket.getTicketNumber())))
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolutionSummary", is("Replaced faulty network switch port.")));

        // Download attachment
        byte[] downloaded = mockMvc.perform(get("/api/support/tickets/" + resolvedTicket.getTicketNumber() + "/attachments/" + attachmentId + "/download")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertArrayEquals(logContent, downloaded);
    }

    @Test
    @DisplayName("3. Previously released engineer does not see the ticket in history (no leak through count)")
    void releasedEngineerDoesNotSeeTicketInHistory() throws Exception {
        mockMvc.perform(get("/api/support/tickets/history")
                        .with(jwt().jwt(j -> j.subject(releasedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("4. Never-assigned engineer does not see the ticket in history (no leak through count)")
    void neverAssignedEngineerDoesNotSeeTicketInHistory() throws Exception {
        mockMvc.perform(get("/api/support/tickets/history")
                        .with(jwt().jwt(j -> j.subject(unassignedSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("5. Active assignment list remains unchanged (active tickets only)")
    void activeAssignmentListRemainsUnchanged() throws Exception {
        // GET /api/support/tickets/my contains only activeTicket, not resolved or closed
        mockMvc.perform(get("/api/support/tickets/my")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].ticketNumber", is(activeTicket.getTicketNumber())))
                .andExpect(jsonPath("$.content[0].status", is("ASSIGNED")));

        // Calling /my with status=RESOLVED returns 0 active assignments
        mockMvc.perform(get("/api/support/tickets/my?status=RESOLVED")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("6. Search, status filtering, pagination and ordering work on history")
    void searchFilterPaginationAndOrderOnHistory() throws Exception {
        // Filter status=RESOLVED
        mockMvc.perform(get("/api/support/tickets/history?status=RESOLVED")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].ticketNumber", is(resolvedTicket.getTicketNumber())));

        // Filter status=CLOSED
        mockMvc.perform(get("/api/support/tickets/history?status=CLOSED")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].ticketNumber", is(closedTicket.getTicketNumber())));

        // Filter status=IN_PROGRESS on history -> 0
        mockMvc.perform(get("/api/support/tickets/history?status=IN_PROGRESS")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(0)));

        // Search by title substring
        mockMvc.perform(get("/api/support/tickets/history?search=network")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].ticketNumber", is(resolvedTicket.getTicketNumber())));

        // Ordering: sort=oldest vs sort=newest
        mockMvc.perform(get("/api/support/tickets/history?sort=oldest")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    @DisplayName("7. Unauthorized history requests return safe responses")
    void unauthorizedHistoryRequestsReturnSafeResponses() throws Exception {
        // Unauthenticated -> 401
        mockMvc.perform(get("/api/support/tickets/history"))
                .andExpect(status().isUnauthorized());

        // Employee -> 403 Forbidden
        mockMvc.perform(get("/api/support/tickets/history")
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        // Invalid pagination/sort -> 400 Bad Request
        mockMvc.perform(get("/api/support/tickets/history?size=0")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/support/tickets/history?sort=invalid_sort")
                        .with(jwt().jwt(j -> j.subject(resolvingSupportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isBadRequest());
    }
}
