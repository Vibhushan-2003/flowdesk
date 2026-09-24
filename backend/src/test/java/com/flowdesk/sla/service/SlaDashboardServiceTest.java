package com.flowdesk.sla.service;

import com.flowdesk.sla.domain.SlaEvent;
import com.flowdesk.sla.domain.SlaEventType;
import com.flowdesk.sla.dto.SlaDashboardResponse;
import com.flowdesk.sla.repository.SlaEventRepository;
import com.flowdesk.sla.repository.projection.SlaPriorityBreachCount;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaDashboardServiceTest {

    @Mock
    private TicketRepository
            ticketRepository;

    @Mock
    private SlaEventRepository
            slaEventRepository;

    private SlaDashboardService
            service;

    @BeforeEach
    void setUp() {
        service =
                new SlaDashboardService(
                        ticketRepository,
                        slaEventRepository
                );
    }

    @Test
    void dashboardBuildsOperationalMetrics() {
        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T06:00:00Z"
                );

        when(
                ticketRepository
                        .countByStatusIn(
                                any()
                        )
        ).thenReturn(12L);

        when(
                ticketRepository
                        .countCurrentResponseSlaBreaches(
                                eq(evaluatedAt),
                                any()
                        )
        ).thenReturn(3L);

        when(
                ticketRepository
                        .countCurrentResolutionSlaBreaches(
                                eq(evaluatedAt),
                                any()
                        )
        ).thenReturn(1L);

        when(
                ticketRepository
                        .countKnownResponseSlaSamples()
        ).thenReturn(8L);

        when(
                ticketRepository
                        .countMetResponseSlaSamples()
        ).thenReturn(7L);

        when(
                ticketRepository
                        .countKnownResolutionSlaSamples()
        ).thenReturn(5L);

        when(
                ticketRepository
                        .countMetResolutionSlaSamples()
        ).thenReturn(4L);

        SlaPriorityBreachCount critical =
                mock(
                        SlaPriorityBreachCount.class
                );

        SlaPriorityBreachCount high =
                mock(
                        SlaPriorityBreachCount.class
                );

        when(critical.getPriority())
                .thenReturn(
                        TicketPriority.CRITICAL
                );

        when(critical.getBreachCount())
                .thenReturn(1L);

        when(high.getPriority())
                .thenReturn(
                        TicketPriority.HIGH
                );

        when(high.getBreachCount())
                .thenReturn(2L);

        when(
                ticketRepository
                        .countCurrentSlaBreachesByPriority(
                                eq(evaluatedAt),
                                any(),
                                any()
                        )
        ).thenReturn(
                List.of(
                        critical,
                        high
                )
        );

        Ticket ticket =
                mock(Ticket.class);

        SlaEvent event =
                mock(SlaEvent.class);

        OffsetDateTime occurredAt =
                OffsetDateTime.parse(
                        "2026-09-24T05:30:00Z"
                );

        when(event.getTicket())
                .thenReturn(ticket);

        when(event.getEventType())
                .thenReturn(
                        SlaEventType
                                .RESPONSE_BREACHED
                );

        when(event.getOccurredAt())
                .thenReturn(occurredAt);

        when(ticket.getTicketNumber())
                .thenReturn("FD-000011");

        when(ticket.getPriority())
                .thenReturn(
                        TicketPriority.MEDIUM
                );

        when(ticket.getStatus())
                .thenReturn(
                        TicketStatus.ASSIGNED
                );

        when(
                slaEventRepository
                        .findRecentSlaEvents(
                                any(Pageable.class)
                        )
        ).thenReturn(
                List.of(event)
        );

        SlaDashboardResponse response =
                service.getDashboard(
                        evaluatedAt
                );

        assertEquals(
                12L,
                response.activeTickets()
        );

        assertEquals(
                3L,
                response.responseBreached()
        );

        assertEquals(
                1L,
                response.resolutionBreached()
        );

        assertEquals(
                87.5,
                response.responseCompliancePercent()
        );

        assertEquals(
                80.0,
                response.resolutionCompliancePercent()
        );

        assertEquals(
                1L,
                response.breachedByPriority()
                        .get(
                                TicketPriority.CRITICAL
                        )
        );

        assertEquals(
                2L,
                response.breachedByPriority()
                        .get(
                                TicketPriority.HIGH
                        )
        );

        assertEquals(
                0L,
                response.breachedByPriority()
                        .get(
                                TicketPriority.MEDIUM
                        )
        );

        assertEquals(
                0L,
                response.breachedByPriority()
                        .get(
                                TicketPriority.LOW
                        )
        );

        assertEquals(
                1,
                response.recentBreaches()
                        .size()
        );

        assertEquals(
                "FD-000011",
                response.recentBreaches()
                        .get(0)
                        .ticketNumber()
        );

        assertEquals(
                occurredAt,
                response.recentBreaches()
                        .get(0)
                        .occurredAt()
        );
    }

    @Test
    void complianceIsNullWhenNoKnownSamplesExist() {
        OffsetDateTime evaluatedAt =
                OffsetDateTime.parse(
                        "2026-09-24T06:00:00Z"
                );

        when(
                ticketRepository
                        .countByStatusIn(
                                any()
                        )
        ).thenReturn(0L);

        when(
                ticketRepository
                        .countCurrentResponseSlaBreaches(
                                eq(evaluatedAt),
                                any()
                        )
        ).thenReturn(0L);

        when(
                ticketRepository
                        .countCurrentResolutionSlaBreaches(
                                eq(evaluatedAt),
                                any()
                        )
        ).thenReturn(0L);

        when(
                ticketRepository
                        .countKnownResponseSlaSamples()
        ).thenReturn(0L);

        when(
                ticketRepository
                        .countMetResponseSlaSamples()
        ).thenReturn(0L);

        when(
                ticketRepository
                        .countKnownResolutionSlaSamples()
        ).thenReturn(0L);

        when(
                ticketRepository
                        .countMetResolutionSlaSamples()
        ).thenReturn(0L);

        when(
                ticketRepository
                        .countCurrentSlaBreachesByPriority(
                                eq(evaluatedAt),
                                any(),
                                any()
                        )
        ).thenReturn(List.of());

        when(
                slaEventRepository
                        .findRecentSlaEvents(
                                any(Pageable.class)
                        )
        ).thenReturn(List.of());

        SlaDashboardResponse response =
                service.getDashboard(
                        evaluatedAt
                );

        assertNull(
                response.responseCompliancePercent()
        );

        assertNull(
                response.resolutionCompliancePercent()
        );
    }
}
