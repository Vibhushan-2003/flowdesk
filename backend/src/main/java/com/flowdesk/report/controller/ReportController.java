package com.flowdesk.report.controller;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.report.dto.OperationalReportResponse;
import com.flowdesk.report.service.CsvExportService;
import com.flowdesk.report.service.ReportService;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.user.repository.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'TEAM_LEAD')")
public class ReportController {

    private final ReportService reportService;
    private final CsvExportService csvExportService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public ReportController(
            ReportService reportService,
            CsvExportService csvExportService,
            AuditService auditService,
            UserRepository userRepository
    ) {
        this.reportService = reportService;
        this.csvExportService = csvExportService;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @GetMapping("/operational")
    public ResponseEntity<OperationalReportResponse> getOperationalReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        OperationalReportResponse report = reportService.generateOperationalReport(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/operational/csv")
    public ResponseEntity<byte[]> exportOperationalReportCsv(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        OperationalReportResponse report = reportService.generateOperationalReport(startDate, endDate);
        String csvContent = csvExportService.buildOperationalSummaryCsv(report);

        if (jwt != null) {
            UUID userId = UUID.fromString(jwt.getSubject());
            userRepository.findById(userId).ifPresent(user ->
                    auditService.recordUserAction(
                            user,
                            AuditAction.REPORT_EXPORTED,
                            AuditTargetType.TICKET,
                            user.getId(),
                            "REPORT_OPERATIONAL",
                            Map.of("reportType", "OPERATIONAL_SUMMARY",
                                    "startDate", String.valueOf(report.dateRange().startDate()),
                                    "endDate", String.valueOf(report.dateRange().endDate()))
                    )
            );
        }

        String filename = "flowdesk-operational-report-" + report.dateRange().startDate() + "-to-" + report.dateRange().endDate() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/tickets/csv")
    public ResponseEntity<byte[]> exportTicketsCsv(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1000") int limit
    ) {
        List<Ticket> tickets = reportService.getTicketsForExport(startDate, endDate, limit);
        String csvContent = csvExportService.buildTicketsListCsv(tickets);

        if (jwt != null) {
            UUID userId = UUID.fromString(jwt.getSubject());
            userRepository.findById(userId).ifPresent(user ->
                    auditService.recordUserAction(
                            user,
                            AuditAction.REPORT_EXPORTED,
                            AuditTargetType.TICKET,
                            user.getId(),
                            "REPORT_TICKETS",
                            Map.of("reportType", "TICKETS_EXPORT", "count", tickets.size())
                    )
            );
        }

        String filename = "flowdesk-tickets-export.csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }
}
