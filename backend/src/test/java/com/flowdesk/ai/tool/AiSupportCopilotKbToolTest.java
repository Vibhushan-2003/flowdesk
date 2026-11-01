package com.flowdesk.ai.tool;

import com.flowdesk.ai.service.AiSupportCopilotToolDispatcher;
import com.flowdesk.kb.domain.KbArticle;
import com.flowdesk.kb.domain.KbCategory;
import com.flowdesk.kb.repository.KbArticleRepository;
import com.flowdesk.ticket.domain.Ticket;
import com.flowdesk.ticket.domain.TicketAssignment;
import com.flowdesk.ticket.repository.TicketAssignmentRepository;
import com.flowdesk.ticket.repository.TicketCommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSupportCopilotKbToolTest {

    @Mock
    private TicketAssignmentRepository ticketAssignmentRepository;

    @Mock
    private TicketCommentRepository ticketCommentRepository;

    @Mock
    private KbArticleRepository kbArticleRepository;

    private AiSupportCopilotToolService toolService;
    private AiSupportCopilotToolDispatcher toolDispatcher;

    @BeforeEach
    void setUp() {
        toolService = new AiSupportCopilotToolService(
                ticketAssignmentRepository,
                ticketCommentRepository,
                kbArticleRepository
        );
        toolDispatcher = new AiSupportCopilotToolDispatcher(toolService);
    }

    @Test
    @DisplayName("SEARCH_KNOWLEDGE_BASE tool: searches published KB articles using ticket title")
    void searchKnowledgeBaseReturnsSummaries() {
        UUID engineerId = UUID.randomUUID();
        String ticketNumber = "FD-000100";

        TicketAssignment assignment = mock(TicketAssignment.class);
        Ticket ticket = mock(Ticket.class);
        when(assignment.getTicket()).thenReturn(ticket);
        when(ticket.getTitle()).thenReturn("Cannot connect to corporate VPN");

        when(ticketAssignmentRepository.findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                ticketNumber, engineerId
        )).thenReturn(Optional.of(assignment));

        KbArticle article = mock(KbArticle.class);
        KbCategory category = mock(KbCategory.class);
        when(article.getTitle()).thenReturn("VPN Setup and Troubleshooting");
        when(article.getSummary()).thenReturn("Steps to configure and diagnose AnyConnect VPN.");
        when(article.getSlug()).thenReturn("vpn-setup-and-troubleshooting");
        when(article.getCategory()).thenReturn(category);
        when(category.getName()).thenReturn("Networking");

        when(kbArticleRepository.findDeflectionSuggestions("Cannot connect to corporate VPN", 3))
                .thenReturn(List.of(article));

        Object result = toolDispatcher.createExecutor(engineerId, ticketNumber)
                .execute(AiSupportCopilotTool.SEARCH_KNOWLEDGE_BASE);

        assertThat(result).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<AiSupportCopilotKbArticleSummary> summaries = (List<AiSupportCopilotKbArticleSummary>) result;

        assertThat(summaries).hasSize(1);
        AiSupportCopilotKbArticleSummary summary = summaries.get(0);
        assertThat(summary.title()).isEqualTo("VPN Setup and Troubleshooting");
        assertThat(summary.summary()).isEqualTo("Steps to configure and diagnose AnyConnect VPN.");
        assertThat(summary.slug()).isEqualTo("vpn-setup-and-troubleshooting");
        assertThat(summary.categoryName()).isEqualTo("Networking");
    }

    @Test
    @DisplayName("SEARCH_KNOWLEDGE_BASE tool: rejects unassigned engineer with 404")
    void searchKnowledgeBaseRejectsUnassignedEngineer() {
        UUID engineerId = UUID.randomUUID();
        String ticketNumber = "FD-000100";

        when(ticketAssignmentRepository.findByTicket_TicketNumberAndAssignedToUser_IdAndReleasedAtIsNull(
                ticketNumber, engineerId
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                toolDispatcher.createExecutor(engineerId, ticketNumber)
                        .execute(AiSupportCopilotTool.SEARCH_KNOWLEDGE_BASE)
        ).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Ticket not found or it is no longer assigned to you");

        verifyNoInteractions(kbArticleRepository);
    }
}

