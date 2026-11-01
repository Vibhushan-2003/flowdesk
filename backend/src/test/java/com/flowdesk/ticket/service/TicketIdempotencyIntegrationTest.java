package com.flowdesk.ticket.service;

import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.ticket.dto.CreateTicketRequest;
import com.flowdesk.ticket.dto.TicketResponse;
import com.flowdesk.ticket.repository.TicketIdempotencyRepository;
import com.flowdesk.ticket.repository.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("Ticket Submission Idempotency Integration Tests")
class TicketIdempotencyIntegrationTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketIdempotencyRepository ticketIdempotencyRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userA;
    private UUID userB;

    @BeforeEach
    void setUp() {
        userA = createUser("idempotency-user-a-" + UUID.randomUUID().toString().substring(0, 8) + "@flowdesk.test");
        userB = createUser("idempotency-user-b-" + UUID.randomUUID().toString().substring(0, 8) + "@flowdesk.test");
    }

    @AfterEach
    void tearDown() {
        if (userA != null && userB != null) {
            jdbc.update("DELETE FROM ticket_assignments WHERE ticket_id IN (SELECT id FROM tickets WHERE created_by_user_id IN (?, ?))", userA, userB);
            jdbc.update("DELETE FROM ticket_idempotency_records WHERE user_id IN (?, ?)", userA, userB);
            jdbc.update("DELETE FROM tickets WHERE created_by_user_id IN (?, ?)", userA, userB);
        }
    }

    private UUID createUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO users (id, email, password_hash, first_name, last_name, status)
                VALUES (?, ?, 'hash', 'Test', 'User', 'ACTIVE')
                """,
                userId, email
        );
        UUID roleId = jdbc.queryForObject(
                "SELECT id FROM roles WHERE code = 'EMPLOYEE'",
                UUID.class
        );
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)",
                userId, roleId
        );
        return userId;
    }

    @Test
    @DisplayName("Repeated submission with identical key and payload must return original ticket without duplicate creation")
    void sameKeyReplayReturnsOriginalTicket() {
        String key = "test-key-" + UUID.randomUUID();
        CreateTicketRequest request = new CreateTicketRequest(
                TicketType.INCIDENT,
                "VPN connectivity issue",
                "Cannot connect to office VPN from home"
        );

        long initialTicketCount = ticketRepository.count();

        // First submission
        TicketResponse firstResponse = ticketService.createTicket(userA, request, key);
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.ticketNumber()).startsWith("FD-");
        assertThat(ticketRepository.count()).isEqualTo(initialTicketCount + 1);

        // Replay submission with same key and request
        TicketResponse secondResponse = ticketService.createTicket(userA, request, key);
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.id()).isEqualTo(firstResponse.id());
        assertThat(secondResponse.ticketNumber()).isEqualTo(firstResponse.ticketNumber());
        assertThat(secondResponse.title()).isEqualTo(firstResponse.title());

        // Verify no extra ticket was created in the database
        assertThat(ticketRepository.count()).isEqualTo(initialTicketCount + 1);
    }

    @Test
    @DisplayName("Reused key with materially different payload must be rejected with 409 Conflict")
    void reusedKeyWithDifferentPayloadThrowsConflict() {
        String key = "test-key-conflict-" + UUID.randomUUID();
        CreateTicketRequest originalRequest = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Original title",
                "Original description"
        );
        CreateTicketRequest alteredTitle = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Altered title",
                "Original description"
        );
        CreateTicketRequest alteredType = new CreateTicketRequest(
                TicketType.SERVICE_REQUEST,
                "Original title",
                "Original description"
        );

        ticketService.createTicket(userA, originalRequest, key);

        // Altered title
        assertThatThrownBy(() -> ticketService.createTicket(userA, alteredTitle, key))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(rse.getReason()).contains("different request payload");
                });

        // Altered type
        assertThatThrownBy(() -> ticketService.createTicket(userA, alteredType, key))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    @DisplayName("Idempotency key must be scoped to the authenticated user; cross-user reuse is permitted")
    void crossUserKeyReuseIsIsolated() {
        String sharedKey = "shared-key-" + UUID.randomUUID();
        CreateTicketRequest requestA = new CreateTicketRequest(
                TicketType.INCIDENT,
                "User A ticket",
                "Description for User A"
        );
        CreateTicketRequest requestB = new CreateTicketRequest(
                TicketType.INCIDENT,
                "User B ticket",
                "Description for User B"
        );

        TicketResponse responseA = ticketService.createTicket(userA, requestA, sharedKey);
        TicketResponse responseB = ticketService.createTicket(userB, requestB, sharedKey);

        assertThat(responseA.id()).isNotEqualTo(responseB.id());
        assertThat(responseA.ticketNumber()).isNotEqualTo(responseB.ticketNumber());
        assertThat(responseA.createdByUserId()).isEqualTo(userA);
        assertThat(responseB.createdByUserId()).isEqualTo(userB);
    }

    @Test
    @DisplayName("Omitted or null idempotency key creates tickets normally (backwards compatibility)")
    void omittedKeyCreatesTicketsNormally() {
        CreateTicketRequest request1 = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Normal ticket 1",
                "Description 1"
        );
        CreateTicketRequest request2 = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Normal ticket 2",
                "Description 2"
        );

        TicketResponse res1 = ticketService.createTicket(userA, request1, null);
        TicketResponse res2 = ticketService.createTicket(userA, request2, "");

        assertThat(res1.id()).isNotEqualTo(res2.id());
        assertThat(res1.ticketNumber()).isNotEqualTo(res2.ticketNumber());
    }

    @Test
    @DisplayName("Idempotency key exceeding 128 characters must be rejected with 400 Bad Request")
    void excessiveKeyLengthThrowsBadRequest() {
        String longKey = "a".repeat(129);
        CreateTicketRequest request = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Title",
                "Description"
        );

        assertThatThrownBy(() -> ticketService.createTicket(userA, request, longKey))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    @DisplayName("Concurrent requests with the same key cannot both create duplicate tickets")
    void concurrentRequestsDoNotCreateDuplicates() throws Exception {
        String concurrentKey = "concurrent-key-" + UUID.randomUUID();
        CreateTicketRequest request = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Concurrent submission title",
                "Testing concurrency safety under load"
        );

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<TicketResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await();
                return ticketService.createTicket(userA, request, concurrentKey);
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();

        List<TicketResponse> successfulResponses = new ArrayList<>();
        int conflictErrors = 0;

        for (Future<TicketResponse> future : futures) {
            try {
                TicketResponse response = future.get();
                successfulResponses.add(response);
            } catch (Exception e) {
                // Concurrency unique constraint / conflict exception is an acceptable outcome
                conflictErrors++;
            }
        }

        executor.shutdown();

        // At least one submission succeeded
        assertThat(successfulResponses).isNotEmpty();

        // If multiple threads succeeded, they must have returned the exact same ticket ID
        UUID primaryTicketId = successfulResponses.get(0).id();
        for (TicketResponse res : successfulResponses) {
            assertThat(res.id()).isEqualTo(primaryTicketId);
        }

        // Database must contain exactly one ticket with this title for this user
        long matchingTickets = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE created_by_user_id = ? AND title = ?",
                Long.class,
                userA,
                request.title()
        );
        assertThat(matchingTickets).isEqualTo(1);
    }

    @Test
    @DisplayName("Audit events are not duplicated during idempotent replay")
    void replayDoesNotDuplicateAuditEvents() {
        String key = "audit-key-" + UUID.randomUUID();
        CreateTicketRequest request = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Audit idempotency check",
                "Verifying audit log entry is not duplicated"
        );

        TicketResponse first = ticketService.createTicket(userA, request, key);
        TicketResponse second = ticketService.createTicket(userA, request, key);

        assertThat(first.id()).isEqualTo(second.id());

        // Count audit logs for TICKET_CREATED on this ticket
        long auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE actor_user_id = ? AND action = 'TICKET_CREATED' AND target_id = ?",
                Long.class,
                userA,
                first.id()
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Failed submission does not leave an uncompleted record blocking retry")
    void failedSubmissionDoesNotBlockSubsequentRetryWithSameKey() {
        String key = "retry-key-" + UUID.randomUUID();
        CreateTicketRequest invalidRequest = new CreateTicketRequest(
                TicketType.INCIDENT,
                "", // Blank title causes IllegalArgumentException or validation error
                "Description"
        );

        // Attempt invalid creation
        assertThatThrownBy(() -> ticketService.createTicket(userA, invalidRequest, key))
                .isInstanceOf(Exception.class);

        // Verify no idempotency record was persisted
        long recordCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket_idempotency_records WHERE user_id = ? AND idempotency_key = ?",
                Long.class,
                userA,
                key
        );
        assertThat(recordCount).isEqualTo(0);

        // Now retry with corrected request using the exact same key
        CreateTicketRequest validRequest = new CreateTicketRequest(
                TicketType.INCIDENT,
                "Corrected valid title",
                "Description"
        );
        TicketResponse successful = ticketService.createTicket(userA, validRequest, key);
        assertThat(successful).isNotNull();
        assertThat(successful.title()).isEqualTo("Corrected valid title");
    }
}
