package com.flowdesk.report.controller;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.report.dto.OperationalReportResponse;
import com.flowdesk.report.service.CsvExportService;
import com.flowdesk.report.service.ReportService;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock
    private ReportService reportService;

    @Mock
    private CsvExportService csvExportService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReportController reportController;

    @Test
    @DisplayName("getOperationalReport: delegates to ReportService and returns 200 OK")
    void getOperationalReport() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        OperationalReportResponse report = mock(OperationalReportResponse.class);

        when(reportService.generateOperationalReport(start, end)).thenReturn(report);

        ResponseEntity<OperationalReportResponse> response = reportController.getOperationalReport(start, end);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(report);
    }

    @Test
    @DisplayName("exportOperationalReportCsv: generates CSV and returns attachment headers")
    void exportOperationalReportCsv() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        UUID userId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        User user = mock(User.class);

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);

        OperationalReportResponse report = mock(OperationalReportResponse.class);
        OperationalReportResponse.DateRangeDto dateRange =
                new OperationalReportResponse.DateRangeDto(start, end, OffsetDateTime.now(ZoneOffset.UTC));
        when(report.dateRange()).thenReturn(dateRange);

        when(reportService.generateOperationalReport(start, end)).thenReturn(report);
        when(csvExportService.buildOperationalSummaryCsv(report)).thenReturn("CSV_CONTENT");

        ResponseEntity<byte[]> response = reportController.exportOperationalReportCsv(jwt, start, end);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment; filename=\"flowdesk-operational-report-2026-10-01-to-2026-10-31.csv\"");
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("CSV_CONTENT");

        verify(auditService).recordUserAction(
                eq(user),
                eq(AuditAction.REPORT_EXPORTED),
                eq(AuditTargetType.TICKET),
                eq(userId),
                eq("REPORT_OPERATIONAL"),
                any()
        );
    }

    @Test
    @DisplayName("exportTicketsCsv: generates tickets CSV with attachment headers")
    void exportTicketsCsv() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        UUID userId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        User user = mock(User.class);

        when(jwt.getSubject()).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(user.getId()).thenReturn(userId);

        Ticket ticket = mock(Ticket.class);
        when(reportService.getTicketsForExport(start, end, 500)).thenReturn(List.of(ticket));
        when(csvExportService.buildTicketsListCsv(List.of(ticket))).thenReturn("TICKET_CSV");

        ResponseEntity<byte[]> response = reportController.exportTicketsCsv(jwt, start, end, 500);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment; filename=\"flowdesk-tickets-export.csv\"");
        assertThat(new String(response.getBody(), StandardCharsets.UTF_8)).isEqualTo("TICKET_CSV");

        verify(auditService).recordUserAction(
                eq(user),
                eq(AuditAction.REPORT_EXPORTED),
                eq(AuditTargetType.TICKET),
                eq(userId),
                eq("REPORT_TICKETS"),
                any()
        );
    }
}

