package com.flowdesk.report.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record OperationalReportResponse(
        DateRangeDto dateRange,
        TicketVolumeMetrics ticketVolume,
        Map<String, Long> volumeByStatus,
        Map<String, Long> volumeByPriority,
        List<DailyTrendPoint> dailyTrends,
        ResolutionDurationMetrics resolutionMetrics,
        SlaComplianceMetrics slaCompliance,
        List<EngineerWorkloadDto> engineerWorkloads,
        KbDeflectionMetrics kbDeflection
) {
    public record DateRangeDto(
            LocalDate startDate,
            LocalDate endDate,
            OffsetDateTime generatedAt
    ) {}

    public record TicketVolumeMetrics(
            long totalCreated,
            long totalResolved,
            long openBacklog
    ) {}

    public record DailyTrendPoint(
            LocalDate date,
            long created,
            long resolved
    ) {}

    public record ResolutionDurationMetrics(
            long resolvedCount,
            Double avgDurationHours,
            Double medianDurationHours,
            Map<String, Double> avgDurationByPriorityHours
    ) {}

    public record SlaComplianceMetrics(
            long totalResolved,
            long compliantResolved,
            double complianceRatePercent,
            long responseBreaches,
            long resolutionBreaches
    ) {}

    public record EngineerWorkloadDto(
            UUID engineerId,
            String engineerName,
            String engineerEmail,
            long activeAssignedTickets,
            long assignedInPeriod,
            long resolvedInPeriod
    ) {}

    public record KbDeflectionMetrics(
            long totalArticles,
            long totalViews,
            long totalDeflections,
            double deflectionRatePercent,
            List<TopDeflectedArticleDto> topArticles
    ) {}

    public record TopDeflectedArticleDto(
            UUID id,
            String title,
            String slug,
            String categoryName,
            int viewCount,
            int deflectionCount
    ) {}
}

