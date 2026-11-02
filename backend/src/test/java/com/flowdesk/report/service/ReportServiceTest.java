package com.flowdesk.report.service;

import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.domain.KbArticle;
import com.flowdesk.kb.domain.KbCategory;
import com.flowdesk.kb.repository.KbArticleRepository;
import com.flowdesk.report.dto.OperationalReportResponse;
import com.flowdesk.report.repository.ReportQueryRepository;
import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportQueryRepository reportQueryRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private KbArticleRepository kbArticleRepository;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportService(reportQueryRepository, ticketRepository, kbArticleRepository);
    }

    @Test
    @DisplayName("generateOperationalReport: rejects startDate after endDate")
    void rejectsInvalidDateOrder() {
        LocalDate start = LocalDate.of(2026, 10, 15);
        LocalDate end = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> reportService.generateOperationalReport(start, end))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must not be after end date");
    }

    @Test
    @DisplayName("generateOperationalReport: rejects date range exceeding 90 days")
    void rejectsRangeExceeding90Days() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 5, 1);

        assertThatThrownBy(() -> reportService.generateOperationalReport(start, end))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("exceeds maximum allowable range of 90 days");
    }

    @Test
    @DisplayName("generateOperationalReport: handles empty datasets gracefully with zeroed statistics")
    void handlesEmptyDatasetGracefully() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 7);

        when(reportQueryRepository.countVolumeByStatus(any(), any())).thenReturn(List.of());
        when(reportQueryRepository.countVolumeByPriority(any(), any())).thenReturn(List.of());
        when(reportQueryRepository.countDailyCreated(any(), any())).thenReturn(List.of());
        when(reportQueryRepository.countDailyResolved(any(), any())).thenReturn(List.of());
        when(ticketRepository.countByStatusIn(any())).thenReturn(0L);
        when(reportQueryRepository.getOverallAvgResolutionDurationSeconds(any(), any())).thenReturn(null);
        when(reportQueryRepository.getAvgResolutionDurationByPriority(any(), any())).thenReturn(List.of());
        when(reportQueryRepository.countSlaEventsByType(any(), any())).thenReturn(List.of());
        when(reportQueryRepository.countResolvedTicketsWithResolutionBreach(any(), any())).thenReturn(0L);
        when(reportQueryRepository.getEngineerWorkloads(any(), any())).thenReturn(List.of());
        when(kbArticleRepository.findAll()).thenReturn(List.of());

        OperationalReportResponse report = reportService.generateOperationalReport(start, end);

        assertThat(report).isNotNull();
        assertThat(report.ticketVolume().totalCreated()).isEqualTo(0L);
        assertThat(report.ticketVolume().totalResolved()).isEqualTo(0L);
        assertThat(report.ticketVolume().openBacklog()).isEqualTo(0L);
        assertThat(report.slaCompliance().complianceRatePercent()).isEqualTo(100.0);
        assertThat(report.resolutionMetrics().avgDurationHours()).isEqualTo(0.0);
        assertThat(report.kbDeflection().deflectionRatePercent()).isEqualTo(0.0);
        assertThat(report.dailyTrends()).hasSize(7); // 7 continuous days
    }

    @Test
    @DisplayName("generateOperationalReport: correctly computes metrics and SLA compliance rate")
    void computesMetricsAndSlaCompliance() {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 2);

        // Daily resolved
        ReportQueryRepository.DateCount resolvedCount = mock(ReportQueryRepository.DateCount.class);
        when(resolvedCount.getDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(resolvedCount.getCount()).thenReturn(10L);
        when(reportQueryRepository.countDailyResolved(any(), any())).thenReturn(List.of(resolvedCount));

        // 10 resolved, 2 breached -> 8 compliant (80.0%)
        when(reportQueryRepository.countResolvedTicketsWithResolutionBreach(any(), any())).thenReturn(2L);
        when(reportQueryRepository.getOverallAvgResolutionDurationSeconds(any(), any())).thenReturn(7200.0); // 2 hours

        // Top article
        KbArticle article = mock(KbArticle.class);
        KbCategory category = mock(KbCategory.class);
        when(article.getStatus()).thenReturn(ArticleStatus.PUBLISHED);
        when(article.getId()).thenReturn(UUID.randomUUID());
        when(article.getTitle()).thenReturn("Password Reset Instructions");
        when(article.getSlug()).thenReturn("password-reset-instructions");
        when(article.getCategory()).thenReturn(category);
        when(category.getName()).thenReturn("Account Access");
        when(article.getViewCount()).thenReturn(50);
        when(article.getDeflectionCount()).thenReturn(12);

        when(kbArticleRepository.findAll()).thenReturn(List.of(article));

        OperationalReportResponse report = reportService.generateOperationalReport(start, end);

        assertThat(report.ticketVolume().totalResolved()).isEqualTo(10L);
        assertThat(report.resolutionMetrics().avgDurationHours()).isEqualTo(2.0);
        assertThat(report.slaCompliance().compliantResolved()).isEqualTo(8L);
        assertThat(report.slaCompliance().complianceRatePercent()).isEqualTo(80.0);
        assertThat(report.kbDeflection().totalDeflections()).isEqualTo(12L);
        assertThat(report.kbDeflection().topArticles()).hasSize(1);
        assertThat(report.kbDeflection().topArticles().get(0).title()).isEqualTo("Password Reset Instructions");
    }
}

