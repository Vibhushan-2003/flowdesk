package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiResolutionSummaryClient;
import com.flowdesk.ai.client.AiResolutionSummaryClientException;
import com.flowdesk.ai.client.AiResolutionSummaryClientRequest;
import com.flowdesk.ai.client.AiResolutionSummaryClientResult;
import com.flowdesk.ai.client.AiResolutionSummaryConversationMessage;
import com.flowdesk.ai.client.AiResolutionSummaryMessageRole;

import com.flowdesk.ai.dto.AiResolutionSummaryResponse;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketStatus;
import com.flowdesk.ticket.domain.TicketType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiResolutionSummaryServiceTest {

    @Mock
    private AiResolutionSummaryClient
            aiResolutionSummaryClient;

    @Mock
    private AiResolutionSummaryContextService
            contextService;

    @Mock
    private AiResolutionSummaryCompletionService
            completionService;

    private AiResolutionSummaryService
            service;

    @BeforeEach
    void setUp() {

        service =
                new AiResolutionSummaryService(
                        aiResolutionSummaryClient,
                        contextService,
                        completionService
                );
    }

    @Test
    void generateDraftUsesPrivacySafeContextAndCompletesGeneration() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        List<AiResolutionSummaryConversationMessage>
                conversation =
                List.of(
                        new AiResolutionSummaryConversationMessage(
                                AiResolutionSummaryMessageRole.REQUESTER,
                                "I still cannot access payroll."
                        ),

                        new AiResolutionSummaryConversationMessage(
                                AiResolutionSummaryMessageRole.SUPPORT,
                                "The payroll access permissions were corrected."
                        ),

                        new AiResolutionSummaryConversationMessage(
                                AiResolutionSummaryMessageRole.REQUESTER,
                                "I can access payroll now."
                        )
                );

        AiResolutionSummaryGenerationContext context =
                new AiResolutionSummaryGenerationContext(
                        ticketId,
                        "FD-000068",
                        TicketType.INCIDENT,
                        TicketPriority.HIGH,
                        TicketStatus.IN_PROGRESS,
                        "Payroll access issue",
                        "Requester cannot access payroll.",
                        conversation
                );

        AiResolutionSummaryClientResult clientResult =
                new AiResolutionSummaryClientResult(
                        "The payroll access permissions were corrected and the requester confirmed successful access.",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite"
                );

        AiResolutionSummaryResponse expectedResponse =
                new AiResolutionSummaryResponse(
                        "FD-000068",
                        clientResult.draft(),
                        clientResult.provider(),
                        clientResult.model(),
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        )
                );

        when(
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000068"
                        )
        ).thenReturn(
                context
        );

        when(
                aiResolutionSummaryClient
                        .generateDraft(
                                any(
                                        AiResolutionSummaryClientRequest.class
                                )
                        )
        ).thenReturn(
                clientResult
        );

        when(
                completionService
                        .completeGeneration(
                                engineerId,
                                ticketId,
                                clientResult,
                                "resolution-summary-v1"
                        )
        ).thenReturn(
                expectedResponse
        );

        AiResolutionSummaryResponse response =
                service.generateDraft(
                        engineerId,
                        "FD-000068"
                );

        assertSame(
                expectedResponse,
                response
        );

        ArgumentCaptor<AiResolutionSummaryClientRequest>
                requestCaptor =
                ArgumentCaptor.forClass(
                        AiResolutionSummaryClientRequest.class
                );

        verify(
                aiResolutionSummaryClient
        ).generateDraft(
                requestCaptor.capture()
        );

        AiResolutionSummaryClientRequest request =
                requestCaptor.getValue();

        assertEquals(
                TicketType.INCIDENT,
                request.type()
        );

        assertEquals(
                TicketPriority.HIGH,
                request.priority()
        );

        assertEquals(
                TicketStatus.IN_PROGRESS,
                request.status()
        );

        assertEquals(
                "Payroll access issue",
                request.title()
        );

        assertEquals(
                "Requester cannot access payroll.",
                request.description()
        );

        assertEquals(
                conversation,
                request.conversation()
        );

        verify(
                completionService
        ).completeGeneration(
                engineerId,
                ticketId,
                clientResult,
                "resolution-summary-v1"
        );
    }

    @Test
    void providerFailureReturnsServiceUnavailableWithoutCompletion() {

        UUID engineerId =
                UUID.randomUUID();

        AiResolutionSummaryGenerationContext context =
                createContext(
                        UUID.randomUUID(),
                        "FD-000069"
                );

        when(
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000069"
                        )
        ).thenReturn(
                context
        );

        when(
                aiResolutionSummaryClient
                        .generateDraft(
                                any(
                                        AiResolutionSummaryClientRequest.class
                                )
                        )
        ).thenThrow(
                new AiResolutionSummaryClientException(
                        "Provider timeout"
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.generateDraft(
                                        engineerId,
                                        "FD-000069"
                                )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(
                "AI resolution summary drafting is temporarily unavailable",
                exception.getReason()
        );

        verifyNoInteractions(
                completionService
        );
    }

    @Test
    void nullProviderResultReturnsServiceUnavailableWithoutCompletion() {

        UUID engineerId =
                UUID.randomUUID();

        AiResolutionSummaryGenerationContext context =
                createContext(
                        UUID.randomUUID(),
                        "FD-000070"
                );

        when(
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000070"
                        )
        ).thenReturn(
                context
        );

        when(
                aiResolutionSummaryClient
                        .generateDraft(
                                any(
                                        AiResolutionSummaryClientRequest.class
                                )
                        )
        ).thenReturn(
                null
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.generateDraft(
                                        engineerId,
                                        "FD-000070"
                                )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(
                "AI resolution summary drafting is temporarily unavailable",
                exception.getReason()
        );

        verifyNoInteractions(
                completionService
        );

        verify(
                contextService
        ).prepareGeneration(
                engineerId,
                "FD-000070"
        );

        verify(
                aiResolutionSummaryClient
        ).generateDraft(
                any(
                        AiResolutionSummaryClientRequest.class
                )
        );

        verifyNoMoreInteractions(
                contextService,
                aiResolutionSummaryClient
        );
    }

    private AiResolutionSummaryGenerationContext
            createContext(
                    UUID ticketId,
                    String ticketNumber
            ) {

        return new AiResolutionSummaryGenerationContext(
                ticketId,
                ticketNumber,
                TicketType.INCIDENT,
                TicketPriority.MEDIUM,
                TicketStatus.IN_PROGRESS,
                "Printer connectivity issue",
                "Requester cannot connect to the office printer.",
                List.of()
        );
    }
}