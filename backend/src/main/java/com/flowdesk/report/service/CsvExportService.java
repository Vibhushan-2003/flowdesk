package com.flowdesk.report.service;

import com.flowdesk.report.dto.OperationalReportResponse;
import com.flowdesk.ticket.domain.Ticket;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CsvExportService {

    public String buildOperationalSummaryCsv(OperationalReportResponse report) {
        StringBuilder csv = new StringBuilder();

        // 1. Header & Period Metadata
        csv.append("FlowDesk Operational Report\n");
        csv.append("Report Period,").append(report.dateRange().startDate()).append(" to ").append(report.dateRange().endDate()).append("\n");
        csv.append("Generated At,").append(report.dateRange().generatedAt()).append("\n\n");

        // 2. Volume Summary
        csv.append("=== TICKET VOLUME SUMMARY ===\n");
        csv.append("Metric,Count\n");
        csv.append("Tickets Created in Period,").append(report.ticketVolume().totalCreated()).append("\n");
        csv.append("Tickets Resolved in Period,").append(report.ticketVolume().totalResolved()).append("\n");
        csv.append("Current Open Backlog,").append(report.ticketVolume().openBacklog()).append("\n\n");

        // 3. Status Breakdown
        csv.append("=== TICKET STATUS BREAKDOWN ===\n");
        csv.append("Status,Count\n");
        for (Map.Entry<String, Long> entry : report.volumeByStatus().entrySet()) {
            csv.append(sanitize(entry.getKey())).append(",").append(entry.getValue()).append("\n");
        }
        csv.append("\n");

        // 4. Priority Breakdown
        csv.append("=== TICKET PRIORITY BREAKDOWN ===\n");
        csv.append("Priority,Count\n");
        for (Map.Entry<String, Long> entry : report.volumeByPriority().entrySet()) {
            csv.append(sanitize(entry.getKey())).append(",").append(entry.getValue()).append("\n");
        }
        csv.append("\n");

        // 5. Resolution & SLA Metrics
        csv.append("=== RESOLUTION & SLA METRICS ===\n");
        csv.append("Metric,Value\n");
        csv.append("Resolved Tickets Count,").append(report.resolutionMetrics().resolvedCount()).append("\n");
        csv.append("Average Resolution Time (Hours),").append(report.resolutionMetrics().avgDurationHours()).append("\n");
        csv.append("SLA Compliance Rate (%),").append(report.slaCompliance().complianceRatePercent()).append("%\n");
        csv.append("Compliant Resolved Tickets,").append(report.slaCompliance().compliantResolved()).append("\n");
        csv.append("Response SLA Breaches,").append(report.slaCompliance().responseBreaches()).append("\n");
        csv.append("Resolution SLA Breaches,").append(report.slaCompliance().resolutionBreaches()).append("\n\n");

        // 6. Support Engineer Workload
        csv.append("=== SUPPORT ENGINEER WORKLOAD ===\n");
        csv.append("Engineer Name,Email,Active Assigned,Assigned in Period,Resolved in Period\n");
        for (OperationalReportResponse.EngineerWorkloadDto ew : report.engineerWorkloads()) {
            csv.append(sanitize(ew.engineerName())).append(",")
                    .append(sanitize(ew.engineerEmail())).append(",")
                    .append(ew.activeAssignedTickets()).append(",")
                    .append(ew.assignedInPeriod()).append(",")
                    .append(ew.resolvedInPeriod()).append("\n");
        }
        csv.append("\n");

        // 7. Knowledge Base Deflections
        csv.append("=== KNOWLEDGE BASE DEFLECTION METRICS ===\n");
        csv.append("Total Published Articles,").append(report.kbDeflection().totalArticles()).append("\n");
        csv.append("Total Article Views,").append(report.kbDeflection().totalViews()).append("\n");
        csv.append("Total Self-Service Deflections,").append(report.kbDeflection().totalDeflections()).append("\n");
        csv.append("Deflection Rate (%),").append(report.kbDeflection().deflectionRatePercent()).append("%\n\n");

        csv.append("Top Deflected Articles\n");
        csv.append("Title,Category,Views,Deflections\n");
        for (OperationalReportResponse.TopDeflectedArticleDto top : report.kbDeflection().topArticles()) {
            csv.append(sanitize(top.title())).append(",")
                    .append(sanitize(top.categoryName())).append(",")
                    .append(top.viewCount()).append(",")
                    .append(top.deflectionCount()).append("\n");
        }

        return csv.toString();
    }

    public String buildTicketsListCsv(List<Ticket> tickets) {
        StringBuilder csv = new StringBuilder();
        csv.append("Ticket Number,Type,Title,Priority,Status,Created At,Resolved At,Requester Email\n");

        for (Ticket ticket : tickets) {
            String requesterEmail = ticket.getCreatedByUser() != null ? ticket.getCreatedByUser().getEmail() : "";
            csv.append(sanitize(ticket.getTicketNumber())).append(",")
                    .append(sanitize(ticket.getType() != null ? ticket.getType().name() : "")).append(",")
                    .append(sanitize(ticket.getTitle())).append(",")
                    .append(sanitize(ticket.getPriority() != null ? ticket.getPriority().name() : "")).append(",")
                    .append(sanitize(ticket.getStatus() != null ? ticket.getStatus().name() : "")).append(",")
                    .append(ticket.getCreatedAt() != null ? ticket.getCreatedAt().toString() : "").append(",")
                    .append(ticket.getResolvedAt() != null ? ticket.getResolvedAt().toString() : "").append(",")
                    .append(sanitize(requesterEmail)).append("\n");
        }

        return csv.toString();
    }

    /**
     * Sanitizes a string for CSV inclusion and prevents CSV Injection (Formula Injection).
     * If text begins with =, +, -, @, \t, or \r, prepend a single quote (').
     * Encloses text with commas, quotes, or newlines in standard RFC 4180 quotes.
     */
    public String sanitize(String input) {
        if (input == null) {
            return "";
        }

        String value = input;

        // Prevent Formula Injection in Excel/Sheets
        if (!value.isEmpty()) {
            char firstChar = value.charAt(0);
            if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@' || firstChar == '\t' || firstChar == '\r') {
                value = "'" + value;
            }
        }

        // Escape double quotes and wrap in quotes if contains delimiter
        boolean containsSpecialChar = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r") || value.contains("\t");
        if (containsSpecialChar || value.startsWith("'")) {
            value = "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }
}
