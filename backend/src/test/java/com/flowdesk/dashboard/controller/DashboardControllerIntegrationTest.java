package com.flowdesk.dashboard.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Operational Dashboard Controller Integration Tests")
class DashboardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID employeeA;
    private UUID employeeB;
    private UUID engineer;
    private UUID adminUser;

    @BeforeEach
    void setUp() {
        // Clean up any historical test tickets with legacy prefixes if lingering
        jdbc.update("""
            DELETE FROM ticket_assignments WHERE ticket_id IN (
                SELECT id FROM tickets WHERE ticket_number LIKE 'FD-DA-%' OR ticket_number LIKE 'FD-DB-%'
                OR ticket_number LIKE 'FD-DS-%' OR ticket_number LIKE 'FD-DO-%' OR ticket_number LIKE 'FD-DSH-%'
            )
        """);
        jdbc.update("""
            DELETE FROM ticket_idempotency_records WHERE ticket_id IN (
                SELECT id FROM tickets WHERE ticket_number LIKE 'FD-DA-%' OR ticket_number LIKE 'FD-DB-%'
                OR ticket_number LIKE 'FD-DS-%' OR ticket_number LIKE 'FD-DO-%' OR ticket_number LIKE 'FD-DSH-%'
            )
        """);
        jdbc.update("""
            DELETE FROM tickets WHERE ticket_number LIKE 'FD-DA-%' OR ticket_number LIKE 'FD-DB-%'
                OR ticket_number LIKE 'FD-DS-%' OR ticket_number LIKE 'FD-DO-%' OR ticket_number LIKE 'FD-DSH-%'
        """);

        employeeA = createUser("dash-emp-a-" + UUID.randomUUID().toString().substring(0, 8) + "@flowdesk.test", "EMPLOYEE");
        employeeB = createUser("dash-emp-b-" + UUID.randomUUID().toString().substring(0, 8) + "@flowdesk.test", "EMPLOYEE");
        engineer = createUser("dash-eng-" + UUID.randomUUID().toString().substring(0, 8) + "@flowdesk.test", "SUPPORT_ENGINEER");
        adminUser = createUser("dash-admin-" + UUID.randomUUID().toString().substring(0, 8) + "@flowdesk.test", "ADMIN");
    }

    @AfterEach
    void tearDown() {
        if (employeeA != null) {
            jdbc.update("DELETE FROM ticket_assignments WHERE ticket_id IN (SELECT id FROM tickets WHERE created_by_user_id IN (?, ?, ?, ?))", employeeA, employeeB, engineer, adminUser);
            jdbc.update("DELETE FROM ticket_idempotency_records WHERE user_id IN (?, ?, ?, ?)", employeeA, employeeB, engineer, adminUser);
            jdbc.update("DELETE FROM tickets WHERE created_by_user_id IN (?, ?, ?, ?)", employeeA, employeeB, engineer, adminUser);
            jdbc.update("DELETE FROM user_roles WHERE user_id IN (?, ?, ?, ?)", employeeA, employeeB, engineer, adminUser);
            jdbc.update("DELETE FROM users WHERE id IN (?, ?, ?, ?)", employeeA, employeeB, engineer, adminUser);
        }
    }

    private UUID createUser(String email, String roleCode) {
        UUID userId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO users (id, email, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'secret', 'First', 'Last', 'ACTIVE')
                """,
                userId, email
        );
        UUID roleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE code = ?",
                UUID.class,
                roleCode
        );
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                userId, roleId
        );
        return userId;
    }

    private void insertTicket(UUID ticketId, String status, UUID creatorId) {
        String ticketNumber = "FD-DSH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        jdbc.update(
                """
                INSERT INTO tickets (id, ticket_number, type, title, description, priority, status, created_by_user_id)
                VALUES (?, ?, 'INCIDENT', 'Test Ticket', 'Description', 'MEDIUM', ?, ?)
                """,
                ticketId, ticketNumber, status, creatorId
        );
    }

    private void insertAssignment(UUID ticketId, UUID engineerId) {
        jdbc.update(
                """
                INSERT INTO ticket_assignments (id, ticket_id, assigned_to_user_id)
                VALUES (?, ?, ?)
                """,
                UUID.randomUUID(), ticketId, engineerId
        );
    }

    @Test
    @DisplayName("Unauthenticated request to /api/dashboard/stats must be rejected with 401")
    void unauthenticatedRequestRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Employee sees only their own tickets without cross-user leakage")
    void employeeSeesOnlyTheirOwnTickets() throws Exception {
        // Employee A: 1 OPEN, 1 WAITING_FOR_USER, 1 RESOLVED
        insertTicket(UUID.randomUUID(), "OPEN", employeeA);
        insertTicket(UUID.randomUUID(), "WAITING_FOR_USER", employeeA);
        insertTicket(UUID.randomUUID(), "RESOLVED", employeeA);

        // Employee B: 2 OPEN tickets
        insertTicket(UUID.randomUUID(), "OPEN", employeeB);
        insertTicket(UUID.randomUUID(), "OPEN", employeeB);

        mockMvc.perform(get("/api/dashboard/stats")
                        .with(jwt().jwt(builder -> builder.subject(employeeA.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.openTickets", equalTo(2)))
                .andExpect(jsonPath("$.employee.ticketsAwaitingResponse", equalTo(1)))
                .andExpect(jsonPath("$.employee.resolvedTickets", equalTo(1)))
                .andExpect(jsonPath("$.employee.pendingApprovals", nullValue()))
                .andExpect(jsonPath("$.support", nullValue()))
                .andExpect(jsonPath("$.operations", nullValue()));
    }

    @Test
    @DisplayName("Support engineer receives non-null support metrics and queue counts")
    void supportEngineerReceivesSupportMetrics() throws Exception {
        // Create an unassigned OPEN ticket in queue
        insertTicket(UUID.randomUUID(), "OPEN", employeeA);

        // Create an assigned ticket for this engineer
        UUID assignedTicketId = UUID.randomUUID();
        insertTicket(assignedTicketId, "ASSIGNED", employeeA);
        insertAssignment(assignedTicketId, engineer);

        mockMvc.perform(get("/api/dashboard/stats")
                        .with(jwt().jwt(builder -> builder.subject(engineer.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee").isNotEmpty())
                .andExpect(jsonPath("$.support").isNotEmpty())
                .andExpect(jsonPath("$.support.assignedActiveTickets", equalTo(1)))
                .andExpect(jsonPath("$.support.assignedSlaBreaches", equalTo(0)))
                .andExpect(jsonPath("$.operations", nullValue()));
    }

    @Test
    @DisplayName("Administrator receives operations metrics with total active and unassigned queue")
    void adminReceivesOperationsMetrics() throws Exception {
        insertTicket(UUID.randomUUID(), "OPEN", employeeA);
        insertTicket(UUID.randomUUID(), "IN_PROGRESS", employeeA);

        mockMvc.perform(get("/api/dashboard/stats")
                        .with(jwt().jwt(builder -> builder.subject(adminUser.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee").isNotEmpty())
                .andExpect(jsonPath("$.operations").isNotEmpty())
                .andExpect(jsonPath("$.operations.totalActiveTickets").isNumber())
                .andExpect(jsonPath("$.operations.unassignedQueueTickets").isNumber());
    }

    @Test
    @DisplayName("New user with empty ticket history receives clean zero counts without error")
    void emptyDatabaseReturnsZeroCountsCleanly() throws Exception {
        UUID freshUser = createUser("fresh-" + UUID.randomUUID().toString().substring(0, 8) + "@flowdesk.test", "EMPLOYEE");

        mockMvc.perform(get("/api/dashboard/stats")
                        .with(jwt().jwt(builder -> builder.subject(freshUser.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.openTickets", equalTo(0)))
                .andExpect(jsonPath("$.employee.ticketsAwaitingResponse", equalTo(0)))
                .andExpect(jsonPath("$.employee.resolvedTickets", equalTo(0)))
                .andExpect(jsonPath("$.support", nullValue()))
                .andExpect(jsonPath("$.operations", nullValue()));
    }
}

