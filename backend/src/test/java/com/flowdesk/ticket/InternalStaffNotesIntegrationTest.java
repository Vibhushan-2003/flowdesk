package com.flowdesk.ticket;

import com.flowdesk.ai.client.AiReplyDraftConversationMessage;
import com.flowdesk.ai.service.AiReplyDraftContextService;
import com.flowdesk.notification.domain.Notification;
import com.flowdesk.notification.repository.NotificationRepository;
import com.flowdesk.ticket.domain.CommentVisibility;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketComment;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;
import com.flowdesk.ticket.repository.TicketRepository;
import com.flowdesk.user.domain.Role;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.RoleRepository;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Internal Staff Notes Security and Visibility Integration Tests")
class InternalStaffNotesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAssignmentRepository assignmentRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AiReplyDraftContextService aiReplyDraftContextService;

    private User employee;
    private User assignedEngineer;
    private User unassignedEngineer;
    private User teamLead;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));
        Role leadRole = roleRepository.findByCode(RoleCode.TEAM_LEAD)
                .orElseThrow(() -> new IllegalStateException("TEAM_LEAD role missing"));

        employee = createUser("emp-notes", employeeRole);
        assignedEngineer = createUser("sup-assigned", supportRole);
        unassignedEngineer = createUser("sup-unassigned", supportRole);
        teamLead = createUser("lead-notes", leadRole);

        String ticketNum = "FD-NOTE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ticket = new Ticket(ticketNum, TicketType.INCIDENT, "Internal Notes Test Ticket", "Description", employee);
        ticket.markAssigned();
        ticket = ticketRepository.saveAndFlush(ticket);

        TicketAssignment assignment = new TicketAssignment(ticket, assignedEngineer);
        assignmentRepository.saveAndFlush(assignment);
    }

    private User createUser(String prefix, Role role) {
        UUID id = UUID.randomUUID();
        User user = new User(
                prefix + "-" + id + "@flowdesk-test.invalid",
                "hashedpassword",
                prefix.toUpperCase(),
                "User"
        );
        user.getRoles().add(role);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("Employee cannot forge internal visibility flag")
    void employeeCannotCreateInternalNotes() throws Exception {
        mockMvc.perform(post("/api/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Attempted forged internal note\",\"visibility\":\"INTERNAL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Authorized support engineer can create internal note and it does not notify employee")
    void authorizedSupportCanCreateInternalNoteWithoutNotification() throws Exception {
        long notificationsBefore = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientUser().getId().equals(employee.getId()))
                .count();

        // 1. Post internal note
        mockMvc.perform(post("/api/support/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Private triage diagnostics - DB query slow\",\"visibility\":\"INTERNAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("INTERNAL"))
                .andExpect(jsonPath("$.body").value("Private triage diagnostics - DB query slow"));

        // Verify zero notifications created for employee
        long notificationsAfterInternal = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientUser().getId().equals(employee.getId()))
                .count();
        assertEquals(notificationsBefore, notificationsAfterInternal, "Internal note must not notify employee");

        // 2. Post public support reply
        mockMvc.perform(post("/api/support/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Public reply: We are checking the DB configuration.\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));

        // Verify public reply DOES notify employee
        long notificationsAfterPublic = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientUser().getId().equals(employee.getId()))
                .count();
        assertEquals(notificationsBefore + 1, notificationsAfterPublic, "Public reply must notify employee");
    }

    @Test
    @DisplayName("Employee cannot retrieve internal notes through ticket comments API")
    void employeeCannotRetrieveInternalNotes() throws Exception {
        // Create 1 public comment and 1 internal note directly in DB
        TicketComment publicComment = new TicketComment(ticket, employee, "Public requester question", CommentVisibility.PUBLIC);
        TicketComment internalNote = new TicketComment(ticket, assignedEngineer, "Private staff analysis", CommentVisibility.INTERNAL);
        commentRepository.saveAndFlush(publicComment);
        commentRepository.saveAndFlush(internalNote);

        // Employee fetches comments
        mockMvc.perform(get("/api/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].body").value("Public requester question"))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"));
    }

    @Test
    @DisplayName("Authorized support engineer retrieves both public and internal notes")
    void authorizedSupportCanRetrieveAllNotes() throws Exception {
        TicketComment publicComment = new TicketComment(ticket, employee, "Public requester question", CommentVisibility.PUBLIC);
        TicketComment internalNote = new TicketComment(ticket, assignedEngineer, "Private staff analysis", CommentVisibility.INTERNAL);
        commentRepository.saveAndFlush(publicComment);
        commentRepository.saveAndFlush(internalNote);

        // Support engineer fetches comments
        mockMvc.perform(get("/api/support/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[1].visibility").value("INTERNAL"));
    }

    @Test
    @DisplayName("Unauthorized support engineer cannot access private notes or comments of another ticket")
    void unauthorizedSupportCannotAccessAnotherTicketNotes() throws Exception {
        mockMvc.perform(get("/api/support/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(unassignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/support/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(unassignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Intruder note\",\"visibility\":\"INTERNAL\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Team lead can add and view internal notes across tickets")
    void teamLeadCanAddAndRetrieveInternalNotes() throws Exception {
        mockMvc.perform(post("/api/support/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(teamLead.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Team Lead review note: Escalated to engineering.\",\"visibility\":\"INTERNAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("INTERNAL"));

        mockMvc.perform(get("/api/support/tickets/" + ticket.getTicketNumber() + "/comments")
                        .with(jwt().jwt(j -> j.subject(teamLead.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Team Lead review note: Escalated to engineering."))
                .andExpect(jsonPath("$[0].visibility").value("INTERNAL"));
    }

    @Test
    @DisplayName("AI reply draft context service strictly filters out internal notes")
    void aiContextNeverIncludesInternalNotes() {
        TicketComment publicComment = new TicketComment(ticket, employee, "Public network issue description", CommentVisibility.PUBLIC);
        TicketComment internalNote = new TicketComment(ticket, assignedEngineer, "CONFIDENTIAL SECRET: Customer router password is 1234", CommentVisibility.INTERNAL);
        commentRepository.saveAndFlush(publicComment);
        commentRepository.saveAndFlush(internalNote);

        var aiContext = aiReplyDraftContextService.prepareGeneration(assignedEngineer.getId(), ticket.getTicketNumber());

        List<AiReplyDraftConversationMessage> messages = aiContext.conversation();
        assertEquals(1, messages.size(), "AI context must contain only public comments");
        assertEquals("Public network issue description", messages.get(0).body());
        assertTrue(messages.stream().noneMatch(m -> m.body().contains("CONFIDENTIAL SECRET")),
                "AI context must NEVER leak internal staff note bodies");
    }
}
