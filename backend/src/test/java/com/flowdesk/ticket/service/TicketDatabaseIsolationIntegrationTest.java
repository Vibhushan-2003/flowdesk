
package com.flowdesk.ticket.service;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.ticket.dto.SupportTicketSummaryResponse;
import com.flowdesk.ticket.dto.TicketSummaryResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(
        named = "CI",
        matches = "true"
)
class TicketDatabaseIsolationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private SupportTicketService supportTicketService;

    private UUID employeeA;
    private UUID employeeB;
    private UUID engineerA;
    private UUID engineerB;

    private String ticketA;
    private String ticketB;

    private UUID assignmentA;
    private UUID assignmentB;

    @BeforeEach
    void setUp() {

        employeeA = createUser("employee-a");
        employeeB = createUser("employee-b");

        engineerA = createUser("engineer-a");
        engineerB = createUser("engineer-b");

        ticketA = createTicket(
                employeeA,
                "Employee A VPN problem"
        );

        ticketB = createTicket(
                employeeB,
                "Employee B VPN problem"
        );

        assignmentA = createAssignment(
                ticketA,
                engineerA
        );

        assignmentB = createAssignment(
                ticketB,
                engineerB
        );
    }

    @Test
    void requesterSearchCannotLeakAnotherUsersTickets() {

        PageResponse<TicketSummaryResponse> resultA =
                ticketService.getMyTickets(
                        employeeA,
                        0,
                        10,
                        "VPN",
                        null,
                        null,
                        "newest"
                );

        assertEquals(1, resultA.totalElements());
        assertEquals(1, resultA.content().size());

        assertEquals(
                ticketA,
                resultA.content().get(0).ticketNumber()
        );

        PageResponse<TicketSummaryResponse> resultB =
                ticketService.getMyTickets(
                        employeeB,
                        0,
                        10,
                        "VPN",
                        null,
                        null,
                        "newest"
                );

        assertEquals(1, resultB.totalElements());

        assertEquals(
                ticketB,
                resultB.content().get(0).ticketNumber()
        );
    }

    @Test
    void requesterCannotOpenAnotherUsersTicket() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ticketService.getMyTicket(
                                employeeA,
                                ticketB
                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                ticketA,
                ticketService
                        .getMyTicket(employeeA, ticketA)
                        .ticketNumber()
        );
    }

    @Test
    void supportSearchOnlyReturnsOwnAssignments() {

        PageResponse<SupportTicketSummaryResponse> resultA =
                supportTicketService.getMyAssignedTickets(
                        engineerA,
                        0,
                        10,
                        "VPN",
                        null,
                        null,
                        "newest"
                );

        assertEquals(1, resultA.totalElements());

        assertEquals(
                ticketA,
                resultA.content().get(0).ticketNumber()
        );

        PageResponse<SupportTicketSummaryResponse> resultB =
                supportTicketService.getMyAssignedTickets(
                        engineerB,
                        0,
                        10,
                        "VPN",
                        null,
                        null,
                        "newest"
                );

        assertEquals(1, resultB.totalElements());

        assertEquals(
                ticketB,
                resultB.content().get(0).ticketNumber()
        );
    }

    @Test
    void supportEngineerCannotOpenAnotherEngineersTicket() {

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> supportTicketService
                                .getMyAssignedTicket(
                                        engineerA,
                                        ticketB
                                )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        assertEquals(
                ticketA,
                supportTicketService
                        .getMyAssignedTicket(
                                engineerA,
                                ticketA
                        )
                        .ticketNumber()
        );
    }

    @Test
    void releasedAssignmentMustNotGrantAccess() {

        jdbc.update(
                """
                UPDATE ticket_assignments
                SET released_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                assignmentA
        );

        PageResponse<SupportTicketSummaryResponse> result =
                supportTicketService.getMyAssignedTickets(
                        engineerA,
                        0,
                        10,
                        null,
                        null,
                        null,
                        "newest"
                );

        assertEquals(0, result.totalElements());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> supportTicketService
                                .getMyAssignedTicket(
                                        engineerA,
                                        ticketA
                                )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    private UUID createUser(String label) {

        UUID id = UUID.randomUUID();

        String email =
                label + "-" + id + "@flowdesk-test.invalid";

        jdbc.update(
                """
                INSERT INTO users (
                    id,
                    email,
                    password_hash,
                    first_name,
                    last_name,
                    status
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                email,
                "integration-test-only",
                "Integration",
                "Test",
                "ACTIVE"
        );

        return id;
    }

    private String createTicket(
            UUID createdByUserId,
            String title
    ) {

        UUID ticketId = UUID.randomUUID();

        String number =
                "IT-" + ticketId.toString()
                        .substring(0, 12)
                        .toUpperCase();

        jdbc.update(
                """
                INSERT INTO tickets (
                    id,
                    ticket_number,
                    type,
                    title,
                    description,
                    priority,
                    status,
                    created_by_user_id
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                ticketId,
                number,
                "INCIDENT",
                title,
                "Database isolation integration test",
                "MEDIUM",
                "ASSIGNED",
                createdByUserId
        );

        return number;
    }

    private UUID createAssignment(
            String ticketNumber,
            UUID engineerId
    ) {

        UUID assignmentId = UUID.randomUUID();

        UUID ticketId = jdbc.queryForObject(
                """
                SELECT id
                FROM tickets
                WHERE ticket_number = ?
                """,
                UUID.class,
                ticketNumber
        );

        assertNotNull(ticketId);

        jdbc.update(
                """
                INSERT INTO ticket_assignments (
                    id,
                    ticket_id,
                    assigned_to_user_id
                )
                VALUES (?, ?, ?)
                """,
                assignmentId,
                ticketId,
                engineerId
        );

        return assignmentId;
    }
}
