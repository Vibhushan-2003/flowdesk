package com.flowdesk.report.service;

import com.flowdesk.report.dto.OperationalReportResponse;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;
import com.flowdesk.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvExportServiceTest {

    private final CsvExportService csvExportService = new CsvExportService();

    @Test
    @DisplayName("sanitize: should prevent CSV formula injection by prepending single quote")
    void sanitizeFormulaInjection() {
        assertThat(csvExportService.sanitize("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(csvExportService.sanitize("+cmd|' /C calc'!A0")).isEqualTo("\"'+cmd|' /C calc'!A0\"");
        assertThat(csvExportService.sanitize("-5*5")).isEqualTo("\"'-5*5\"");
        assertThat(csvExportService.sanitize("@SUM(A1:A10)")).isEqualTo("\"'@SUM(A1:A10)\"");
        assertThat(csvExportService.sanitize("\tmalicious")).isEqualTo("\"'\tmalicious\"");
    }

    @Test
    @DisplayName("sanitize: should escape double quotes and wrap in quotes when containing delimiters")
    void sanitizeDelimitersAndQuotes() {
        assertThat(csvExportService.sanitize("Normal Text")).isEqualTo("Normal Text");
        assertThat(csvExportService.sanitize("Hello, World")).isEqualTo("\"Hello, World\"");
        assertThat(csvExportService.sanitize("He said \"Stop\"")).isEqualTo("\"He said \"\"Stop\"\"\"");
        assertThat(csvExportService.sanitize(null)).isEqualTo("");
    }

    @Test
    @DisplayName("buildOperationalSummaryCsv: should include all section headers and formatted values")
    void buildOperationalSummaryCsv() {
        OperationalReportResponse report = new OperationalReportResponse(
                new OperationalReportResponse.DateRangeDto(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 31),
                        OffsetDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneOffset.UTC)
                ),
                new OperationalReportResponse.TicketVolumeMetrics(100L, 85L, 15L),
                Map.of("OPEN", 10L, "RESOLVED", 85L),
                Map.of("CRITICAL", 5L, "HIGH", 20L),
                List.of(new OperationalReportResponse.DailyTrendPoint(LocalDate.of(2026, 10, 1), 5L, 3L)),
                new OperationalReportResponse.ResolutionDurationMetrics(85L, 4.2, 3.8, Map.of("CRITICAL", 1.5)),
                new OperationalReportResponse.SlaComplianceMetrics(85L, 80L, 94.1, 2L, 5L),
                List.of(new OperationalReportResponse.EngineerWorkloadDto(
                        UUID.randomUUID(),
                        "Alice Smith",
                        "alice@flowdesk.test",
                        3L,
                        25L,
                        22L
                )),
                new OperationalReportResponse.KbDeflectionMetrics(
                        12L,
                        450L,
                        35L,
                        25.9,
                        List.of(new OperationalReportResponse.TopDeflectedArticleDto(
                                UUID.randomUUID(),
                                "VPN Setup Guide",
                                "vpn-setup-guide",
                                "Network",
                                120,
                                18
                        ))
                )
        );

        String csv = csvExportService.buildOperationalSummaryCsv(report);

        assertThat(csv).contains("FlowDesk Operational Report");
        assertThat(csv).contains("2026-10-01 to 2026-10-31");
        assertThat(csv).contains("=== TICKET VOLUME SUMMARY ===");
        assertThat(csv).contains("Tickets Created in Period,100");
        assertThat(csv).contains("Tickets Resolved in Period,85");
        assertThat(csv).contains("=== RESOLUTION & SLA METRICS ===");
        assertThat(csv).contains("Average Resolution Time (Hours),4.2");
        assertThat(csv).contains("SLA Compliance Rate (%),94.1%");
        assertThat(csv).contains("=== SUPPORT ENGINEER WORKLOAD ===");
        assertThat(csv).contains("Alice Smith,alice@flowdesk.test,3,25,22");
        assertThat(csv).contains("=== KNOWLEDGE BASE DEFLECTION METRICS ===");
        assertThat(csv).contains("VPN Setup Guide,Network,120,18");
    }

    @Test
    @DisplayName("buildTicketsListCsv: should format ticket rows safely and sanitize fields")
    void buildTicketsListCsv() {
        Ticket ticket = mock(Ticket.class);
        User creator = mock(User.class);

        when(ticket.getTicketNumber()).thenReturn("FD-000042");
        when(ticket.getType()).thenReturn(TicketType.INCIDENT);
        when(ticket.getTitle()).thenReturn("=SUM(A1) Injection Test, with comma");
        when(ticket.getPriority()).thenReturn(TicketPriority.HIGH);
        when(ticket.getStatus()).thenReturn(TicketStatus.RESOLVED);
        when(ticket.getCreatedAt()).thenReturn(OffsetDateTime.of(2026, 10, 15, 9, 30, 0, 0, ZoneOffset.UTC));
        when(ticket.getResolvedAt()).thenReturn(OffsetDateTime.of(2026, 10, 15, 11, 45, 0, 0, ZoneOffset.UTC));
        when(ticket.getCreatedByUser()).thenReturn(creator);
        when(creator.getEmail()).thenReturn("requester@flowdesk.test");

        String csv = csvExportService.buildTicketsListCsv(List.of(ticket));

        assertThat(csv).startsWith("Ticket Number,Type,Title,Priority,Status,Created At,Resolved At,Requester Email\n");
        assertThat(csv).contains("FD-000042,INCIDENT");
        // Check that formula injection was sanitized with leading quote
        assertThat(csv).contains("\"'=SUM(A1) Injection Test, with comma\"");
        assertThat(csv).contains("HIGH,RESOLVED");
        assertThat(csv).contains("requester@flowdesk.test");
    }
}

