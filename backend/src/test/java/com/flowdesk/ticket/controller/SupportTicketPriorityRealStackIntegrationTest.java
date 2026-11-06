package com.flowdesk.ticket.controller;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditLog;
import com.flowdesk.audit.repository.AuditLogRepository;
import com.flowdesk.audit.repository.AuditLogRepository;
import com.flowdesk.notification.repository.NotificationRepository;
import com.flowdesk.sla.domain.SlaEvent;
import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.sla.domain.SlaPolicy;
import com.flowdesk.sla.repository.SlaEventRepository;
import com.flowdesk.sla.repository.SlaPolicyRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportTicketPriorityRealStackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAssignmentRepository assignmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SlaPolicyRepository slaPolicyRepository;

    @Autowired
    private SlaEventRepository slaEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditEventRepository;

    private User employee;
    private User assignedEngineer;
    private User otherEngineer;
    private User teamLead;

    private Ticket activeTicket;
    private TicketAssignment activeAssignment;

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));
        Role leadRole = roleRepository.findByCode(RoleCode.TEAM_LEAD)
                .orElseThrow(() -> new IllegalStateException("TEAM_LEAD role missing"));

        employee = createUser("emp-prio-" + UUID.randomUUID().toString().substring(0, 6), employeeRole);
        assignedEngineer = createUser("sup-assigned-" + UUID.randomUUID().toString().substring(0, 6), supportRole);
        otherEngineer = createUser("sup-other-" + UUID.randomUUID().toString().substring(0, 6), supportRole);
        teamLead = createUser("lead-prio-" + UUID.randomUUID().toString().substring(0, 6), leadRole);

        // Create active incident ticket
        String ticketNum = "FD-P" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
        activeTicket = new Ticket(
                ticketNum,
                TicketType.INCIDENT,
                "Priority Policy Integration Test",
                "Verifying real PostgreSQL SLA recalculation",
                employee,
                TicketPriority.MEDIUM
        );

        SlaPolicy mediumPolicy = slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.MEDIUM)
                .orElseThrow();
        activeTicket.applySlaPolicy(mediumPolicy, OffsetDateTime.now(ZoneOffset.UTC));
        activeTicket.markAssigned();
        activeTicket = ticketRepository.saveAndFlush(activeTicket);

        activeAssignment = new TicketAssignment(activeTicket, assignedEngineer);
        activeAssignment = assignmentRepository.saveAndFlush(activeAssignment);
    }

    @AfterEach
    void tearDown() {
        if (activeTicket != null && activeTicket.getId() != null) {
            notificationRepository.deleteAll(notificationRepository.findAll().stream()
                    .filter(n -> n.getTicket() != null && activeTicket.getId().equals(n.getTicket().getId()))
                    .toList());
            slaEventRepository.deleteAll(slaEventRepository.findAll().stream()
                    .filter(e -> e.getTicket().getId().equals(activeTicket.getId()))
                    .toList());
            assignmentRepository.deleteAll(assignmentRepository.findAll().stream()
                    .filter(a -> a.getTicket().getId().equals(activeTicket.getId()))
                    .toList());
            ticketRepository.deleteById(activeTicket.getId());
        }
    }

    private User createUser(String prefix, Role role) {
        User user = new User(
                prefix + "@flowdesk.test",
                "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
                "Test",
                prefix
        );
        user.assignRole(role);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("1. Employee ticket creation with CRITICAL is rejected with 400 Bad Request")
    void employeeCannotCreateCriticalTicket_returns400() throws Exception {
        mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INCIDENT",
                                  "title": "Outage report",
                                  "description": "Entire network is down",
                                  "priority": "CRITICAL"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Requesters may only submit LOW, MEDIUM, or HIGH urgency. CRITICAL priority requires staff escalation.")));
    }

    @Test
    @DisplayName("2. Employee ticket creation with LOW, MEDIUM, HIGH is accepted and persisted")
    void employeeCanCreateTicketWithAllowedPriorities() throws Exception {
        String res = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INCIDENT",
                                  "title": "Allowed high ticket",
                                  "description": "Important issue",
                                  "priority": "HIGH"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority", is("HIGH")))
                .andReturn().getResponse().getContentAsString();

        String createdTicketNum = com.jayway.jsonpath.JsonPath.read(res, "$.ticketNumber");
        Ticket createdTicket = ticketRepository.findByTicketNumber(createdTicketNum).orElseThrow();
        assertEquals(TicketPriority.HIGH, createdTicket.getPriority());

        // Cleanup
        assignmentRepository.deleteAll(assignmentRepository.findAll().stream()
                .filter(a -> a.getTicket().getId().equals(createdTicket.getId())).toList());
        ticketRepository.deleteById(createdTicket.getId());
    }

    @Test
    @DisplayName("3. Employee cannot call reprioritize endpoint (403 Forbidden)")
    void employeeCannotReprioritize_returns403() throws Exception {
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\"}")
                        .with(jwt().jwt(j -> j.subject(employee.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Unassigned engineer receives 404 Not Found")
    void unassignedEngineerCannotReprioritize_returns404() throws Exception {
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\"}")
                        .with(jwt().jwt(j -> j.subject(otherEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("5. Assigned engineer can reprioritize in ASSIGNED, IN_PROGRESS, and WAITING_FOR_USER")
    void assignedEngineerCanReprioritizeInActiveStates() throws Exception {
        // A. Reprioritize in ASSIGNED
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("HIGH")));

        Ticket reloaded1 = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertEquals(TicketPriority.HIGH, reloaded1.getPriority());

        // B. Transition to IN_PROGRESS and reprioritize to LOW
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"LOW\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("LOW")));

        Ticket reloaded2 = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertEquals(TicketPriority.LOW, reloaded2.getPriority());

        // C. Transition to WAITING_FOR_USER and reprioritize to HIGH
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"WAITING_FOR_USER\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("HIGH")));

        Ticket reloaded3 = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertEquals(TicketPriority.HIGH, reloaded3.getPriority());
    }

    @Test
    @DisplayName("6. Reprioritization is blocked when ticket is RESOLVED (409 Conflict)")
    void cannotReprioritizeWhenResolved_returns409() throws Exception {
        // Transition to IN_PROGRESS then RESOLVED
        activeTicket.transitionSupportStatus(TicketStatus.IN_PROGRESS);
        activeTicket.resolve("Resolved diagnostic test");
        ticketRepository.saveAndFlush(activeTicket);

        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"LOW\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("7. Reprioritization is blocked when an SLA breach event is recorded (409 Conflict)")
    void cannotReprioritizeWhenSlaBreachRecorded_returns409() throws Exception {
        SlaEvent breach = new SlaEvent(activeTicket, SlaEventType.RESPONSE_BREACHED, OffsetDateTime.now(ZoneOffset.UTC));
        slaEventRepository.saveAndFlush(breach);

        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("8. Team Lead can reprioritize tickets assigned to another engineer")
    void teamLeadCanReprioritizeOtherEngineerTicket() throws Exception {
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"CRITICAL\"}")
                        .with(jwt().jwt(j -> j.subject(teamLead.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("CRITICAL")));

        Ticket reloaded = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertEquals(TicketPriority.CRITICAL, reloaded.getPriority());
    }

    @Test
    @DisplayName("9. Priority change persists audit entry with fromPriority and toPriority")
    void priorityChangeRecordsAuditEntry() throws Exception {
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk());

        List<AuditLog> auditEvents = auditEventRepository.findAll().stream()
                .filter(a -> a.getAction() == AuditAction.TICKET_PRIORITY_CHANGED
                        && activeTicket.getId().equals(a.getTargetId()))
                .toList();

        assertFalse(auditEvents.isEmpty(), "Audit event TICKET_PRIORITY_CHANGED should be present");
        AuditLog event = auditEvents.get(auditEvents.size() - 1);
        assertNotNull(event.getMetadata());
        assertEquals("MEDIUM", event.getMetadata().get("fromPriority"));
        assertEquals("HIGH", event.getMetadata().get("toPriority"));
    }

    @Test
    @DisplayName("10. Privileged staff (Support Engineer) can create ticket directly with CRITICAL priority")
    void staffCanCreateCriticalTicket() throws Exception {
        String res = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INCIDENT",
                                  "title": "Critical datacenter outage",
                                  "description": "Entire network datacenter infrastructure failure",
                                  "priority": "CRITICAL"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority", is("CRITICAL")))
                .andReturn().getResponse().getContentAsString();

        String createdTicketNum = com.jayway.jsonpath.JsonPath.read(res, "$.ticketNumber");
        Ticket createdTicket = ticketRepository.findByTicketNumber(createdTicketNum).orElseThrow();
        assertEquals(TicketPriority.CRITICAL, createdTicket.getPriority());

        // Verify SLA deadlines initialized with CRITICAL policy (15m response, 240m resolution)
        assertNotNull(createdTicket.getResponseDueAt());
        assertNotNull(createdTicket.getResolutionDueAt());

        // Cleanup
        assignmentRepository.deleteAll(assignmentRepository.findAll().stream()
                .filter(a -> a.getTicket().getId().equals(createdTicket.getId())).toList());
        ticketRepository.deleteById(createdTicket.getId());
    }

    @Test
    @DisplayName("11. End-to-end SLA recalculation preserves original SLA start time across transitions")
    void slaRecalculationPreservesStartTimeAcrossAllPriorities() throws Exception {
        OffsetDateTime initialResponseDue = activeTicket.getResponseDueAt();
        OffsetDateTime initialResolutionDue = activeTicket.getResolutionDueAt();
        // Medium SLA policy: response 60m, resolution 1440m
        OffsetDateTime expectedStart = initialResolutionDue.minusMinutes(1440);

        // A. Escalate from MEDIUM to CRITICAL (15m response, 240m resolution)
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"CRITICAL\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("CRITICAL")));

        Ticket critTicket = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertEquals(TicketPriority.CRITICAL, critTicket.getPriority());
        assertEquals(expectedStart.plusMinutes(15).toEpochSecond(), critTicket.getResponseDueAt().toEpochSecond());
        assertEquals(expectedStart.plusMinutes(240).toEpochSecond(), critTicket.getResolutionDueAt().toEpochSecond());

        // B. De-escalate from CRITICAL to LOW (240m response, 2880m resolution)
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"LOW\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("LOW")));

        Ticket lowTicket = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertEquals(TicketPriority.LOW, lowTicket.getPriority());
        assertEquals(expectedStart.plusMinutes(240).toEpochSecond(), lowTicket.getResponseDueAt().toEpochSecond());
        assertEquals(expectedStart.plusMinutes(2880).toEpochSecond(), lowTicket.getResolutionDueAt().toEpochSecond());

        // C. Transition to IN_PROGRESS (which records first response), then escalate to HIGH (30m response, 480m resolution)
        // With first response recorded, responseDueAt must remain unchanged (start + 240m from LOW), while resolutionDueAt updates to start + 480m
        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/support/tickets/" + activeTicket.getTicketNumber() + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\": \"HIGH\"}")
                        .with(jwt().jwt(j -> j.subject(assignedEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("HIGH")));

        Ticket highTicket = ticketRepository.findById(activeTicket.getId()).orElseThrow();
        assertEquals(TicketPriority.HIGH, highTicket.getPriority());
        assertNotNull(highTicket.getFirstRespondedAt());
        // responseDueAt kept preserved start + 240m from LOW because first response was already recorded
        assertEquals(expectedStart.plusMinutes(240).toEpochSecond(), highTicket.getResponseDueAt().toEpochSecond());
        assertEquals(expectedStart.plusMinutes(480).toEpochSecond(), highTicket.getResolutionDueAt().toEpochSecond());
    }
}
