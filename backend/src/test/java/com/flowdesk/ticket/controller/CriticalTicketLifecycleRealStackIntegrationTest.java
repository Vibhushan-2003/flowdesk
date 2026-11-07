package com.flowdesk.ticket.controller;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditLog;
import com.flowdesk.audit.repository.AuditLogRepository;
import com.flowdesk.notification.domain.Notification;
import com.flowdesk.notification.repository.NotificationRepository;
import com.flowdesk.sla.domain.SlaEvent;
import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.sla.domain.SlaPolicy;
import com.flowdesk.sla.repository.SlaEventRepository;
import com.flowdesk.sla.repository.SlaPolicyRepository;
import com.flowdesk.sla.service.SlaMonitoringService;
import com.flowdesk.sla.domain.SlaStatus;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CriticalTicketLifecycleRealStackIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketAssignmentRepository assignmentRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

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

    @Autowired
    private SlaMonitoringService slaMonitoringService;

    private User requester;
    private User supportEngineer;
    private User teamLead;

    private final List<UUID> createdTicketIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Role employeeRole = roleRepository.findByCode(RoleCode.EMPLOYEE)
                .orElseThrow(() -> new IllegalStateException("EMPLOYEE role missing"));
        Role supportRole = roleRepository.findByCode(RoleCode.SUPPORT_ENGINEER)
                .orElseThrow(() -> new IllegalStateException("SUPPORT_ENGINEER role missing"));
        Role leadRole = roleRepository.findByCode(RoleCode.TEAM_LEAD)
                .orElseThrow(() -> new IllegalStateException("TEAM_LEAD role missing"));

        requester = createUser("crit-req-" + UUID.randomUUID().toString().substring(0, 6), employeeRole);
        supportEngineer = createUser("crit-sup-" + UUID.randomUUID().toString().substring(0, 6), supportRole);
        teamLead = createUser("crit-lead-" + UUID.randomUUID().toString().substring(0, 6), leadRole);
    }

    @AfterEach
    void tearDown() {
        for (UUID ticketId : createdTicketIds) {
            commentRepository.deleteAll(commentRepository.findAll().stream()
                    .filter(c -> c.getTicket() != null && ticketId.equals(c.getTicket().getId()))
                    .toList());
            notificationRepository.deleteAll(notificationRepository.findAll().stream()
                    .filter(n -> n.getTicket() != null && ticketId.equals(n.getTicket().getId()))
                    .toList());
            slaEventRepository.deleteAll(slaEventRepository.findAll().stream()
                    .filter(e -> e.getTicket() != null && ticketId.equals(e.getTicket().getId()))
                    .toList());
            assignmentRepository.deleteAll(assignmentRepository.findAll().stream()
                    .filter(a -> a.getTicket() != null && ticketId.equals(a.getTicket().getId()))
                    .toList());
            ticketRepository.deleteById(ticketId);
        }
        createdTicketIds.clear();
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
    @DisplayName("1. Complete Critical Ticket Lifecycle: Creation -> SLA +15m/+240m -> Queue -> Claim -> Response Met -> Breach Monitoring -> Resolve")
    void completeCriticalTicketLifecycle_preservesStateAndSlaIntegrity() throws Exception {
        // Step 1: Staff creates CRITICAL ticket directly
        String createRes = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INCIDENT",
                                  "title": "Production Database Primary Offline",
                                  "description": "Failover did not trigger automatically; application pool exhausted.",
                                  "priority": "CRITICAL"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(teamLead.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority", is("CRITICAL")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.responseSlaStatus", is("PENDING")))
                .andExpect(jsonPath("$.resolutionSlaStatus", is("PENDING")))
                .andReturn().getResponse().getContentAsString();

        String ticketNumber = com.jayway.jsonpath.JsonPath.read(createRes, "$.ticketNumber");
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber).orElseThrow();
        createdTicketIds.add(ticket.getId());

        // SLA Policies check: Critical response is 15 minutes, resolution is 240 minutes (4 hours)
        assertNotNull(ticket.getResponseDueAt());
        assertNotNull(ticket.getResolutionDueAt());

        long responseMinutes = Math.round(Duration.between(ticket.getCreatedAt(), ticket.getResponseDueAt()).toMillis() / 60000.0);
        long resolutionMinutes = Math.round(Duration.between(ticket.getCreatedAt(), ticket.getResolutionDueAt()).toMillis() / 60000.0);

        assertEquals(15, responseMinutes, "Critical response SLA must be 15 minutes");
        assertEquals(240, resolutionMinutes, "Critical resolution SLA must be 240 minutes (4 hours)");

        // Step 2: Queue visibility & Ticket status for support staff
        // Since queue is sorted ASC by createdAt (FIFO), locate the page containing this latest ticket
        String queueInitialRes = mockMvc.perform(get("/api/tickets/queue")
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int totalPages = com.jayway.jsonpath.JsonPath.read(queueInitialRes, "$.totalPages");
        int targetPage = Math.max(0, totalPages - 1);

        mockMvc.perform(get("/api/tickets/queue?page=" + targetPage + "&size=10")
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].ticketNumber", hasItem(ticketNumber)))
                .andExpect(jsonPath("$.content[?(@.ticketNumber == '" + ticketNumber + "')].priority", hasItem("CRITICAL")))
                .andExpect(jsonPath("$.content[?(@.ticketNumber == '" + ticketNumber + "')].status", hasItem("OPEN")));

        mockMvc.perform(get("/api/tickets/" + ticketNumber)
                        .with(jwt().jwt(j -> j.subject(teamLead.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("CRITICAL")))
                .andExpect(jsonPath("$.status", is("OPEN")))
                .andExpect(jsonPath("$.responseSlaStatus", is("PENDING")))
                .andExpect(jsonPath("$.resolutionSlaStatus", is("PENDING")));

        // Step 3: Support engineer claims ticket
        mockMvc.perform(post("/api/tickets/" + ticketNumber + "/claim")
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ASSIGNED")))
                .andExpect(jsonPath("$.assignedToEmail", is(supportEngineer.getEmail())));

        Ticket assignedTicket = ticketRepository.findByTicketNumber(ticketNumber).orElseThrow();
        assertEquals(TicketStatus.ASSIGNED, assignedTicket.getStatus());
        assertTrue(assignmentRepository.findByTicket_IdAndReleasedAtIsNull(ticket.getId()).isPresent());

        // Step 4: Engineer begins work and transitions status to IN_PROGRESS -> firstRespondedAt is recorded
        mockMvc.perform(patch("/api/support/tickets/" + ticketNumber + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PROGRESS"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // First staff response comment posted
        mockMvc.perform(post("/api/support/tickets/" + ticketNumber + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "body": "Incident acknowledged. Primary replica logs show WAL replay deadlock.",
                                  "visibility": "PUBLIC"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isCreated());

        Ticket respondedTicket = ticketRepository.findByTicketNumber(ticketNumber).orElseThrow();
        assertNotNull(respondedTicket.getFirstRespondedAt(), "firstRespondedAt must be recorded when work starts");
        assertEquals(SlaStatus.MET, respondedTicket.evaluateResponseSla(OffsetDateTime.now(ZoneOffset.UTC)));

        // Step 5: Read endpoint dynamically returns MET for Response SLA and PENDING for Resolution SLA
        mockMvc.perform(get("/api/support/tickets/" + ticketNumber)
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseSlaStatus", is("MET")))
                .andExpect(jsonPath("$.resolutionSlaStatus", is("PENDING")));

        // Step 6: Breach evaluation with simulated time past resolutionDueAt
        OffsetDateTime futureTimePastResolution = respondedTicket.getResolutionDueAt().plusMinutes(10);

        // Dynamic read evaluation: ticket evaluated in the future must report BREACHED
        assertEquals(SlaStatus.BREACHED, respondedTicket.evaluateResolutionSla(futureTimePastResolution));

        // Test SLA monitoring background service with explicit evaluation time
        int breachCount = slaMonitoringService.monitorBreaches(futureTimePastResolution);
        assertTrue(breachCount >= 1, "SlaMonitoringService must record at least 1 breach event for resolution deadline");

        // Verify SlaEvent row created in database
        List<SlaEvent> slaEvents = slaEventRepository.findAll().stream()
                .filter(e -> e.getTicket().getId().equals(ticket.getId()))
                .toList();
        assertFalse(slaEvents.isEmpty(), "SlaEvent must be persisted in database");
        assertTrue(slaEvents.stream().anyMatch(e -> e.getEventType() == SlaEventType.RESOLUTION_BREACHED));

        // Verify AuditLog row created
        List<AuditLog> auditLogs = auditEventRepository.findAll().stream()
                .filter(a -> ticket.getId().equals(a.getTargetId()))
                .toList();
        assertTrue(auditLogs.stream().anyMatch(a -> a.getAction() == AuditAction.SLA_RESOLUTION_BREACHED));

        // Verify In-app notification created
        List<Notification> notifications = notificationRepository.findAll().stream()
                .filter(n -> n.getTicket() != null && ticket.getId().equals(n.getTicket().getId()))
                .toList();
        assertFalse(notifications.isEmpty(), "In-app notifications must be created on SLA breach");

        // Step 7: Resolve ticket after breach has occurred
        // Manually simulate that deadlines were in the past while preserving responseDueAt <= resolutionDueAt check constraint
        Ticket inProgressTicket = ticketRepository.findByTicketNumber(ticketNumber).orElseThrow();
        OffsetDateTime simulatedPast = OffsetDateTime.now(ZoneOffset.UTC).minusHours(5);
        org.springframework.test.util.ReflectionTestUtils.setField(
                inProgressTicket,
                "responseDueAt",
                simulatedPast.plusMinutes(15)
        );
        org.springframework.test.util.ReflectionTestUtils.setField(
                inProgressTicket,
                "resolutionDueAt",
                simulatedPast.plusMinutes(240) // 1 hour ago
        );
        ticketRepository.saveAndFlush(inProgressTicket);

        // Resolve ticket via official PATCH /api/support/tickets/{ticketNumber}/resolve endpoint
        mockMvc.perform(patch("/api/support/tickets/" + ticketNumber + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resolutionSummary": "Replay deadlock cleared, standby promoted to primary and tested healthy."
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESOLVED")))
                .andExpect(jsonPath("$.resolutionSummary", containsString("Replay deadlock cleared")))
                .andExpect(jsonPath("$.resolutionSlaStatus", is("BREACHED"))); // Breached obligation is preserved!

        // Step 8: Verify persistence of resolution and SLA status
        Ticket resolvedTicket = ticketRepository.findByTicketNumber(ticketNumber).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, resolvedTicket.getStatus());
        assertNotNull(resolvedTicket.getResolvedAt());
        assertEquals(SlaStatus.BREACHED, resolvedTicket.evaluateResolutionSla(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("2. SLA Deadlines for all four priorities LOW, MEDIUM, HIGH, CRITICAL conform to V7 specification")
    void allFourPriorities_conformToV7Deadlines() {
        OffsetDateTime baseTime = OffsetDateTime.of(2026, 11, 7, 10, 0, 0, 0, ZoneOffset.UTC);

        // LOW
        SlaPolicy lowPolicy = slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.LOW).orElseThrow();
        assertEquals(240, lowPolicy.getResponseMinutes(), "LOW response = 240m (4h)");
        assertEquals(2880, lowPolicy.getResolutionMinutes(), "LOW resolution = 2880m (48h)");

        // MEDIUM
        SlaPolicy medPolicy = slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.MEDIUM).orElseThrow();
        assertEquals(60, medPolicy.getResponseMinutes(), "MEDIUM response = 60m (1h)");
        assertEquals(1440, medPolicy.getResolutionMinutes(), "MEDIUM resolution = 1440m (24h)");

        // HIGH
        SlaPolicy highPolicy = slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.HIGH).orElseThrow();
        assertEquals(30, highPolicy.getResponseMinutes(), "HIGH response = 30m");
        assertEquals(480, highPolicy.getResolutionMinutes(), "HIGH resolution = 480m (8h)");

        // CRITICAL
        SlaPolicy critPolicy = slaPolicyRepository.findByPriorityAndActiveTrue(TicketPriority.CRITICAL).orElseThrow();
        assertEquals(15, critPolicy.getResponseMinutes(), "CRITICAL response = 15m");
        assertEquals(240, critPolicy.getResolutionMinutes(), "CRITICAL resolution = 240m (4h)");
    }

    @Test
    @DisplayName("3. Priority escalation to CRITICAL recalculates deadlines and checks permissions")
    void priorityEscalation_recalculatesDeadlinesAndEnforcesPermissions() throws Exception {
        // Employee creates a MEDIUM ticket
        String res = mockMvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "INCIDENT",
                                  "title": "VPN connection drops",
                                  "description": "Happens every 30 minutes",
                                  "priority": "MEDIUM"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(requester.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority", is("MEDIUM")))
                .andReturn().getResponse().getContentAsString();

        String ticketNumber = com.jayway.jsonpath.JsonPath.read(res, "$.ticketNumber");
        Ticket ticket = ticketRepository.findByTicketNumber(ticketNumber).orElseThrow();
        createdTicketIds.add(ticket.getId());

        // Support engineer claims ticket, moving it to ASSIGNED
        mockMvc.perform(post("/api/tickets/" + ticketNumber + "/claim")
                        .with(jwt().jwt(j -> j.subject(supportEngineer.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ASSIGNED")));

        // Employee cannot access priority escalation endpoint (403 Forbidden)
        mockMvc.perform(patch("/api/support/tickets/" + ticketNumber + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "priority": "CRITICAL"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(requester.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());

        // Team Lead escalates ticket to CRITICAL
        mockMvc.perform(patch("/api/support/tickets/" + ticketNumber + "/priority")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "priority": "CRITICAL"
                                }
                                """)
                        .with(jwt().jwt(j -> j.subject(teamLead.getId().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority", is("CRITICAL")));

        Ticket escalated = ticketRepository.findByTicketNumber(ticketNumber).orElseThrow();
        assertEquals(TicketPriority.CRITICAL, escalated.getPriority());

        // Deadlines must reflect CRITICAL SLA (15 min response, 240 min resolution from creation time)
        long responseMinutes = Math.round(Duration.between(escalated.getCreatedAt(), escalated.getResponseDueAt()).toMillis() / 60000.0);
        assertEquals(15, responseMinutes);
    }
}
