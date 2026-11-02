package com.flowdesk.report.service;

import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.domain.KbArticle;
import com.flowdesk.kb.repository.KbArticleRepository;
import com.flowdesk.report.dto.OperationalReportResponse;
import com.flowdesk.report.repository.ReportQueryRepository;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class ReportService {

    private static final int MAX_RANGE_DAYS = 90;
    private static final int DEFAULT_RANGE_DAYS = 30;

    private static final List<TicketStatus> ACTIVE_STATUSES = List.of(
            TicketStatus.OPEN,
            TicketStatus.ASSIGNED,
            TicketStatus.IN_PROGRESS,
            TicketStatus.WAITING_FOR_USER
    );

    private final ReportQueryRepository reportQueryRepository;
    private final TicketRepository ticketRepository;
    private final KbArticleRepository kbArticleRepository;

    public ReportService(
            ReportQueryRepository reportQueryRepository,
            TicketRepository ticketRepository,
            KbArticleRepository kbArticleRepository
    ) {
        this.reportQueryRepository = reportQueryRepository;
        this.ticketRepository = ticketRepository;
        this.kbArticleRepository = kbArticleRepository;
    }

    @Transactional(readOnly = true)
    public OperationalReportResponse generateOperationalReport(LocalDate startDate, LocalDate endDate) {
        LocalDate end = (endDate != null) ? endDate : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = (startDate != null) ? startDate : end.minusDays(DEFAULT_RANGE_DAYS);

        validateDateRange(start, end);

        OffsetDateTime startUtc = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endUtc = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

        // 1. Volume by Status
        Map<String, Long> volumeByStatus = new LinkedHashMap<>();
        for (TicketStatus status : TicketStatus.values()) {
            volumeByStatus.put(status.name(), 0L);
        }
        for (ReportQueryRepository.StatusCount sc : reportQueryRepository.countVolumeByStatus(startUtc, endUtc)) {
            if (sc.getStatus() != null) {
                volumeByStatus.put(sc.getStatus(), sc.getCount());
            }
        }

        // 2. Volume by Priority
        Map<String, Long> volumeByPriority = new LinkedHashMap<>();
        for (TicketPriority priority : TicketPriority.values()) {
            volumeByPriority.put(priority.name(), 0L);
        }
        for (ReportQueryRepository.PriorityCount pc : reportQueryRepository.countVolumeByPriority(startUtc, endUtc)) {
            if (pc.getPriority() != null) {
                volumeByPriority.put(pc.getPriority(), pc.getCount());
            }
        }

        // 3. Daily trends (continuous daily dates from start to end)
        Map<LocalDate, Long> createdByDay = new HashMap<>();
        for (ReportQueryRepository.DateCount dc : reportQueryRepository.countDailyCreated(startUtc, endUtc)) {
            if (dc.getDate() != null) {
                createdByDay.put(dc.getDate(), dc.getCount());
            }
        }

        Map<LocalDate, Long> resolvedByDay = new HashMap<>();
        for (ReportQueryRepository.DateCount dc : reportQueryRepository.countDailyResolved(startUtc, endUtc)) {
            if (dc.getDate() != null) {
                resolvedByDay.put(dc.getDate(), dc.getCount());
            }
        }

        List<OperationalReportResponse.DailyTrendPoint> dailyTrends = new ArrayList<>();
        LocalDate cur = start;
        long totalCreatedInPeriod = 0L;
        long totalResolvedInPeriod = 0L;
        while (!cur.isAfter(end)) {
            long created = createdByDay.getOrDefault(cur, 0L);
            long resolved = resolvedByDay.getOrDefault(cur, 0L);
            totalCreatedInPeriod += created;
            totalResolvedInPeriod += resolved;
            dailyTrends.add(new OperationalReportResponse.DailyTrendPoint(cur, created, resolved));
            cur = cur.plusDays(1);
        }

        long openBacklog = ticketRepository.countByStatusIn(ACTIVE_STATUSES);

        // 4. Resolution Duration Metrics
        Double overallAvgSec = reportQueryRepository.getOverallAvgResolutionDurationSeconds(startUtc, endUtc);
        Double avgDurationHours = overallAvgSec != null
                ? roundToOneDecimal(overallAvgSec / 3600.0)
                : 0.0;

        Double overallMedianSec = reportQueryRepository.getOverallMedianResolutionDurationSeconds(startUtc, endUtc);
        Double medianDurationHours = overallMedianSec != null
                ? roundToOneDecimal(overallMedianSec / 3600.0)
                : 0.0;

        Map<String, Double> avgByPriority = new LinkedHashMap<>();
        for (TicketPriority p : TicketPriority.values()) {
            avgByPriority.put(p.name(), 0.0);
        }
        for (ReportQueryRepository.PriorityAvgDuration pad : reportQueryRepository.getAvgResolutionDurationByPriority(startUtc, endUtc)) {
            if (pad.getPriority() != null && pad.getAvgDurationSeconds() != null) {
                avgByPriority.put(pad.getPriority(), roundToOneDecimal(pad.getAvgDurationSeconds() / 3600.0));
            }
        }

        OperationalReportResponse.ResolutionDurationMetrics resolutionMetrics =
                new OperationalReportResponse.ResolutionDurationMetrics(
                        totalResolvedInPeriod,
                        avgDurationHours,
                        medianDurationHours,
                        avgByPriority
                );

        // 5. SLA Compliance
        long responseBreaches = 0L;
        long resolutionBreaches = 0L;
        for (ReportQueryRepository.EventTypeCount ec : reportQueryRepository.countSlaEventsByType(startUtc, endUtc)) {
            if ("RESPONSE_BREACHED".equalsIgnoreCase(ec.getEventType())) {
                responseBreaches += ec.getCount();
            } else if ("RESOLUTION_BREACHED".equalsIgnoreCase(ec.getEventType())) {
                resolutionBreaches += ec.getCount();
            }
        }

        Long breachedResolvedCount = reportQueryRepository.countResolvedTicketsWithResolutionBreach(startUtc, endUtc);
        long breachedResolved = breachedResolvedCount != null ? breachedResolvedCount : 0L;
        long compliantResolved = Math.max(0L, totalResolvedInPeriod - breachedResolved);
        double complianceRate = totalResolvedInPeriod > 0
                ? roundToOneDecimal((compliantResolved * 100.0) / totalResolvedInPeriod)
                : 100.0;

        OperationalReportResponse.SlaComplianceMetrics slaCompliance =
                new OperationalReportResponse.SlaComplianceMetrics(
                        totalResolvedInPeriod,
                        compliantResolved,
                        complianceRate,
                        responseBreaches,
                        resolutionBreaches
                );

        // 6. Engineer Workload
        List<OperationalReportResponse.EngineerWorkloadDto> engineerWorkloads = new ArrayList<>();
        for (ReportQueryRepository.EngineerWorkload ew : reportQueryRepository.getEngineerWorkloads(startUtc, endUtc)) {
            String name = ((ew.getFirstName() != null ? ew.getFirstName() : "") + " "
                    + (ew.getLastName() != null ? ew.getLastName() : "")).trim();
            if (name.isEmpty()) {
                name = ew.getEmail() != null ? ew.getEmail() : "Support Engineer";
            }
            engineerWorkloads.add(new OperationalReportResponse.EngineerWorkloadDto(
                    ew.getUserId(),
                    name,
                    ew.getEmail(),
                    ew.getActiveAssigned() != null ? ew.getActiveAssigned() : 0L,
                    ew.getAssignedInPeriod() != null ? ew.getAssignedInPeriod() : 0L,
                    ew.getResolvedInPeriod() != null ? ew.getResolvedInPeriod() : 0L
            ));
        }

        // 7. Knowledge Base Deflection Metrics
        List<KbArticle> allPublishedArticles = kbArticleRepository.findAll().stream()
                .filter(a -> a.getStatus() == ArticleStatus.PUBLISHED)
                .toList();

        long totalArticles = allPublishedArticles.size();
        long totalViews = allPublishedArticles.stream().mapToLong(KbArticle::getViewCount).sum();
        long totalDeflections = allPublishedArticles.stream().mapToLong(KbArticle::getDeflectionCount).sum();

        double deflectionRate = (totalDeflections + totalCreatedInPeriod) > 0
                ? roundToOneDecimal((totalDeflections * 100.0) / (totalDeflections + totalCreatedInPeriod))
                : 0.0;

        List<OperationalReportResponse.TopDeflectedArticleDto> topArticles = allPublishedArticles.stream()
                .sorted(Comparator.comparingInt(KbArticle::getDeflectionCount)
                        .thenComparingInt(KbArticle::getViewCount).reversed())
                .limit(5)
                .map(a -> new OperationalReportResponse.TopDeflectedArticleDto(
                        a.getId(),
                        a.getTitle(),
                        a.getSlug(),
                        a.getCategory() != null ? a.getCategory().getName() : "General",
                        a.getViewCount(),
                        a.getDeflectionCount()
                ))
                .toList();

        OperationalReportResponse.KbDeflectionMetrics kbDeflection =
                new OperationalReportResponse.KbDeflectionMetrics(
                        totalArticles,
                        totalViews,
                        totalDeflections,
                        deflectionRate,
                        topArticles
                );

        return new OperationalReportResponse(
                new OperationalReportResponse.DateRangeDto(start, end, nowUtc),
                new OperationalReportResponse.TicketVolumeMetrics(totalCreatedInPeriod, totalResolvedInPeriod, openBacklog),
                volumeByStatus,
                volumeByPriority,
                dailyTrends,
                resolutionMetrics,
                slaCompliance,
                engineerWorkloads,
                kbDeflection
        );
    }

    @Transactional(readOnly = true)
    public List<Ticket> getTicketsForExport(LocalDate startDate, LocalDate endDate, int limit) {
        LocalDate end = (endDate != null) ? endDate : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = (startDate != null) ? startDate : end.minusDays(DEFAULT_RANGE_DAYS);

        validateDateRange(start, end);

        OffsetDateTime startUtc = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endUtc = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        int maxLimit = Math.min(Math.max(limit, 1), 5000);
        return reportQueryRepository.findTicketsForExport(startUtc, endUtc, PageRequest.of(0, maxLimit));
    }

    private void validateDateRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date (" + start + ") must not be after end date (" + end + ")"
            );
        }
        long days = ChronoUnit.DAYS.between(start, end);
        if (days > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date range (" + days + " days) exceeds maximum allowable range of " + MAX_RANGE_DAYS + " days"
            );
        }
    }

    private double roundToOneDecimal(double value) {
        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}

