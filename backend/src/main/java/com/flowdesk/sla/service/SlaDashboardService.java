package com.flowdesk.sla.service;

import com.flowdesk.sla.domain.SlaEvent;
import com.flowdesk.sla.dto.RecentSlaBreachResponse;
import com.flowdesk.sla.dto.SlaDashboardResponse;
import com.flowdesk.sla.repository.SlaEventRepository;

import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class SlaDashboardService {

    private static final int RECENT_BREACH_LIMIT =
            10;

    private static final List<TicketStatus>
            ACTIVE_STATUSES =
            List.of(
                    TicketStatus.OPEN,
                    TicketStatus.ASSIGNED,
                    TicketStatus.IN_PROGRESS,
                    TicketStatus.WAITING_FOR_USER
            );

    private static final List<TicketStatus>
            RESPONSE_MONITORED_STATUSES =
            List.of(
                    TicketStatus.OPEN,
                    TicketStatus.ASSIGNED
            );

    private final TicketRepository
            ticketRepository;

    private final SlaEventRepository
            slaEventRepository;

    public SlaDashboardService(
            TicketRepository ticketRepository,
            SlaEventRepository slaEventRepository
    ) {
        this.ticketRepository =
                ticketRepository;

        this.slaEventRepository =
                slaEventRepository;
    }

    @Transactional(readOnly = true)
    public SlaDashboardResponse getDashboard() {
        return getDashboard(
                OffsetDateTime.now(
                        ZoneOffset.UTC
                )
        );
    }

    @Transactional(readOnly = true)
    SlaDashboardResponse getDashboard(
            OffsetDateTime evaluatedAt
    ) {
        Objects.requireNonNull(
                evaluatedAt,
                "SLA evaluation time is required"
        );

        OffsetDateTime normalizedEvaluationTime =
                evaluatedAt
                        .withOffsetSameInstant(
                                ZoneOffset.UTC
                        );

        long activeTickets =
                ticketRepository
                        .countByStatusIn(
                                ACTIVE_STATUSES
                        );

        long responseBreached =
                ticketRepository
                        .countCurrentResponseSlaBreaches(
                                normalizedEvaluationTime,
                                RESPONSE_MONITORED_STATUSES
                        );

        long resolutionBreached =
                ticketRepository
                        .countCurrentResolutionSlaBreaches(
                                normalizedEvaluationTime,
                                ACTIVE_STATUSES
                        );

        long knownResponseSamples =
                ticketRepository
                        .countKnownResponseSlaSamples();

        long metResponseSamples =
                ticketRepository
                        .countMetResponseSlaSamples();

        long knownResolutionSamples =
                ticketRepository
                        .countKnownResolutionSlaSamples();

        long metResolutionSamples =
                ticketRepository
                        .countMetResolutionSlaSamples();

        Map<TicketPriority, Long>
                breachedByPriority =
                createEmptyPriorityBreakdown();

        ticketRepository
                .countCurrentSlaBreachesByPriority(
                        normalizedEvaluationTime,
                        ACTIVE_STATUSES,
                        RESPONSE_MONITORED_STATUSES
                )
                .forEach(
                        breakdown ->
                                breachedByPriority.put(
                                        breakdown.getPriority(),
                                        breakdown.getBreachCount()
                                )
                );

        List<RecentSlaBreachResponse>
                recentBreaches =
                slaEventRepository
                        .findRecentSlaEvents(
                                PageRequest.of(
                                        0,
                                        RECENT_BREACH_LIMIT
                                )
                        )
                        .stream()
                        .map(
                                this::toRecentBreachResponse
                        )
                        .toList();

        return new SlaDashboardResponse(
                activeTickets,
                responseBreached,
                resolutionBreached,
                calculateCompliancePercent(
                        metResponseSamples,
                        knownResponseSamples
                ),
                calculateCompliancePercent(
                        metResolutionSamples,
                        knownResolutionSamples
                ),
                breachedByPriority,
                recentBreaches
        );
    }

    private Map<TicketPriority, Long>
            createEmptyPriorityBreakdown() {

        Map<TicketPriority, Long> result =
                new EnumMap<>(
                        TicketPriority.class
                );

        for (
                TicketPriority priority
                : TicketPriority.values()
        ) {
            result.put(
                    priority,
                    0L
            );
        }

        return result;
    }

    private Double calculateCompliancePercent(
            long metSamples,
            long knownSamples
    ) {
        if (knownSamples == 0) {
            return null;
        }

        double rawPercent =
                ((double) metSamples
                        / knownSamples)
                        * 100.0;

        return Math.round(
                rawPercent * 10.0
        ) / 10.0;
    }

    private RecentSlaBreachResponse
            toRecentBreachResponse(
                    SlaEvent slaEvent
            ) {

        Ticket ticket =
                slaEvent.getTicket();

        return new RecentSlaBreachResponse(
                ticket.getTicketNumber(),
                slaEvent.getEventType(),
                ticket.getPriority(),
                ticket.getStatus(),
                slaEvent.getOccurredAt()
        );
    }
}
