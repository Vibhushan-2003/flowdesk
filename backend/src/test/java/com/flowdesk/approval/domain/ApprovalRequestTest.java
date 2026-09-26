package com.flowdesk.approval.domain;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketType;

import com.flowdesk.user.domain.User;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalRequestTest {

    @Test
    void serviceRequestStartsPending() {
        Ticket ticket =
                serviceRequestTicket();

        User requester =
                mock(User.class);

        OffsetDateTime requestedAt =
                OffsetDateTime.parse(
                        "2026-09-26T08:00:00Z"
                );

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        requestedAt
                );

        assertEquals(
                ApprovalStatus.PENDING,
                approval.getStatus()
        );

        assertSame(
                ticket,
                approval.getTicket()
        );

        assertSame(
                requester,
                approval.getRequestedByUser()
        );

        assertEquals(
                requestedAt,
                approval.getRequestedAt()
        );

        assertNull(
                approval.getDecidedByUser()
        );

        assertNull(
                approval.getDecisionNote()
        );

        assertNull(
                approval.getDecidedAt()
        );
    }

    @Test
    void incidentCannotCreateApprovalRequest() {
        Ticket ticket =
                mock(Ticket.class);

        User requester =
                mock(User.class);

        when(ticket.getType())
                .thenReturn(
                        TicketType.INCIDENT
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new ApprovalRequest(
                                        ticket,
                                        requester,
                                        OffsetDateTime.parse(
                                                "2026-09-26T08:00:00Z"
                                        )
                                )
                );

        assertEquals(
                "Only service requests require approval",
                exception.getMessage()
        );
    }

    @Test
    void approverCanApprovePendingRequest() {
        Ticket ticket =
                serviceRequestTicket();

        User requester =
                mock(User.class);

        User approver =
                mock(User.class);

        OffsetDateTime requestedAt =
                OffsetDateTime.parse(
                        "2026-09-26T08:00:00Z"
                );

        OffsetDateTime decidedAt =
                OffsetDateTime.parse(
                        "2026-09-26T08:15:00Z"
                );

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        requestedAt
                );

        approval.approve(
                approver,
                "  Approved for business use  ",
                decidedAt
        );

        assertEquals(
                ApprovalStatus.APPROVED,
                approval.getStatus()
        );

        assertSame(
                approver,
                approval.getDecidedByUser()
        );

        assertEquals(
                "Approved for business use",
                approval.getDecisionNote()
        );

        assertEquals(
                decidedAt,
                approval.getDecidedAt()
        );
    }

    @Test
    void rejectionRequiresReason() {
        Ticket ticket =
                serviceRequestTicket();

        User requester =
                mock(User.class);

        User approver =
                mock(User.class);

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        OffsetDateTime.parse(
                                "2026-09-26T08:00:00Z"
                        )
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                approval.reject(
                                        approver,
                                        "   ",
                                        OffsetDateTime.parse(
                                                "2026-09-26T08:10:00Z"
                                        )
                                )
                );

        assertEquals(
                "Rejection reason is required",
                exception.getMessage()
        );

        assertEquals(
                ApprovalStatus.PENDING,
                approval.getStatus()
        );
    }

    @Test
    void requesterCannotDecideOwnRequest() {
        Ticket ticket =
                serviceRequestTicket();

        User requester =
                mock(User.class);

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        OffsetDateTime.parse(
                                "2026-09-26T08:00:00Z"
                        )
                );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                approval.approve(
                                        requester,
                                        null,
                                        OffsetDateTime.parse(
                                                "2026-09-26T08:10:00Z"
                                        )
                                )
                );

        assertEquals(
                "Requester cannot approve or reject their own request",
                exception.getMessage()
        );

        assertEquals(
                ApprovalStatus.PENDING,
                approval.getStatus()
        );
    }

    @Test
    void decidedRequestCannotBeDecidedAgain() {
        Ticket ticket =
                serviceRequestTicket();

        User requester =
                mock(User.class);

        User firstApprover =
                mock(User.class);

        User secondApprover =
                mock(User.class);

        ApprovalRequest approval =
                new ApprovalRequest(
                        ticket,
                        requester,
                        OffsetDateTime.parse(
                                "2026-09-26T08:00:00Z"
                        )
                );

        approval.approve(
                firstApprover,
                null,
                OffsetDateTime.parse(
                        "2026-09-26T08:10:00Z"
                )
        );

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                approval.reject(
                                        secondApprover,
                                        "Changed decision",
                                        OffsetDateTime.parse(
                                                "2026-09-26T08:20:00Z"
                                        )
                                )
                );

        assertEquals(
                "Approval request has already been decided",
                exception.getMessage()
        );

        assertEquals(
                ApprovalStatus.APPROVED,
                approval.getStatus()
        );
    }

    private Ticket serviceRequestTicket() {
        Ticket ticket =
                mock(Ticket.class);

        when(ticket.getType())
                .thenReturn(
                        TicketType.SERVICE_REQUEST
                );

        return ticket;
    }
}