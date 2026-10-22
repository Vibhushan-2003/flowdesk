package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiSupportCopilotClientException;
import com.flowdesk.ai.client.AiSupportCopilotToolExecutor;

import com.flowdesk.ai.tool.AiSupportCopilotConversationMessage;
import com.flowdesk.ai.tool.AiSupportCopilotSlaSnapshot;
import com.flowdesk.ai.tool.AiSupportCopilotTicketOverview;
import com.flowdesk.ai.tool.AiSupportCopilotTool;
import com.flowdesk.ai.tool.AiSupportCopilotToolService;

import com.flowdesk.sla.domain.SlaStatus;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupportCopilotToolDispatcherTest {

    @Mock
    private AiSupportCopilotToolService
            toolService;

    private AiSupportCopilotToolDispatcher
            dispatcher;

    @BeforeEach
    void setUp() {

        dispatcher =
                new AiSupportCopilotToolDispatcher(
                        toolService
                );
    }

    @Test
    void overviewToolRoutesOnlyToOverviewOperation() {

        UUID engineerId =
                UUID.randomUUID();

        AiSupportCopilotTicketOverview expected =
                new AiSupportCopilotTicketOverview(
                        TicketType.INCIDENT,
                        TicketPriority.HIGH,
                        TicketStatus.IN_PROGRESS,
                        "VPN unavailable",
                        "VPN cannot connect."
                );

        when(
                toolService
                        .getTicketOverview(
                                engineerId,
                                "FD-000070"
                        )
        ).thenReturn(
                expected
        );

        AiSupportCopilotToolExecutor executor =
                dispatcher
                        .createExecutor(
                                engineerId,
                                "FD-000070"
                        );

        Object result =
                executor.execute(
                        AiSupportCopilotTool
                                .GET_TICKET_OVERVIEW
                );

        assertSame(
                expected,
                result
        );

        verify(
                toolService
        ).getTicketOverview(
                engineerId,
                "FD-000070"
        );
    }

    @Test
    void conversationToolRoutesOnlyToConversationOperation() {

        UUID engineerId =
                UUID.randomUUID();

        List<AiSupportCopilotConversationMessage>
                expected =
                List.of(
                        new AiSupportCopilotConversationMessage(
                                AiSupportCopilotConversationMessage
                                        .Role.REQUESTER,
                                "The issue still occurs."
                        )
                );

        when(
                toolService
                        .getPublicConversation(
                                engineerId,
                                "FD-000071"
                        )
        ).thenReturn(
                expected
        );

        AiSupportCopilotToolExecutor executor =
                dispatcher
                        .createExecutor(
                                engineerId,
                                "FD-000071"
                        );

        Object result =
                executor.execute(
                        AiSupportCopilotTool
                                .GET_PUBLIC_CONVERSATION
                );

        assertEquals(
                expected,
                result
        );

        verify(
                toolService
        ).getPublicConversation(
                engineerId,
                "FD-000071"
        );
    }

    @Test
    void slaToolRoutesOnlyToSlaOperation() {

        UUID engineerId =
                UUID.randomUUID();

        AiSupportCopilotSlaSnapshot expected =
                new AiSupportCopilotSlaSnapshot(
                        SlaStatus.MET,
                        SlaStatus.PENDING,
                        null,
                        45L
                );

        when(
                toolService
                        .getSlaSnapshot(
                                engineerId,
                                "FD-000072"
                        )
        ).thenReturn(
                expected
        );

        AiSupportCopilotToolExecutor executor =
                dispatcher
                        .createExecutor(
                                engineerId,
                                "FD-000072"
                        );

        Object result =
                executor.execute(
                        AiSupportCopilotTool
                                .GET_SLA_SNAPSHOT
                );

        assertSame(
                expected,
                result
        );

        verify(
                toolService
        ).getSlaSnapshot(
                engineerId,
                "FD-000072"
        );
    }

    @Test
    void nullToolRequestIsRejected() {

        UUID engineerId =
                UUID.randomUUID();

        AiSupportCopilotToolExecutor executor =
                dispatcher
                        .createExecutor(
                                engineerId,
                                "FD-000073"
                        );

        AiSupportCopilotClientException exception =
                assertThrows(
                        AiSupportCopilotClientException.class,
                        () ->
                                executor.execute(
                                        null
                                )
                );

        assertEquals(
                "AI provider requested an invalid Copilot tool",
                exception.getMessage()
        );
    }

    @Test
    void executorRequiresAuthenticatedEngineer() {

        assertThrows(
                NullPointerException.class,
                () ->
                        dispatcher
                                .createExecutor(
                                        null,
                                        "FD-000074"
                                )
        );
    }

    @Test
    void executorRequiresTicketNumber() {

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dispatcher
                                .createExecutor(
                                        UUID.randomUUID(),
                                        "   "
                                )
        );
    }
}