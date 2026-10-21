package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiReplyDraftClient;
import com.flowdesk.ai.client.AiReplyDraftClientException;
import com.flowdesk.ai.client.AiReplyDraftClientRequest;
import com.flowdesk.ai.client.AiReplyDraftClientResult;
import com.flowdesk.ai.client.AiReplyDraftConversationMessage;
import com.flowdesk.ai.client.AiReplyDraftMessageRole;

import com.flowdesk.ai.dto.AiReplyDraftResponse;

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
class AiReplyDraftServiceTest {

    @Mock
    private AiReplyDraftClient
            aiReplyDraftClient;

    @Mock
    private AiReplyDraftContextService
            contextService;

    @Mock
    private AiReplyDraftCompletionService
            completionService;

    private AiReplyDraftService
            service;

    @BeforeEach
    void setUp() {

        service =
                new AiReplyDraftService(
                        aiReplyDraftClient,
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

        List<AiReplyDraftConversationMessage>
                conversation =
                List.of(
                        new AiReplyDraftConversationMessage(
                                AiReplyDraftMessageRole.REQUESTER,
                                "The issue is still happening."
                        ),

                        new AiReplyDraftConversationMessage(
                                AiReplyDraftMessageRole.SUPPORT,
                                "Please restart the VPN client."
                        )
                );

        AiReplyDraftGenerationContext context =
                new AiReplyDraftGenerationContext(
                        ticketId,
                        "FD-000050",
                        TicketType.INCIDENT,
                        TicketPriority.HIGH,
                        TicketStatus.IN_PROGRESS,
                        "VPN unavailable",
                        "Users cannot connect to VPN.",
                        conversation
                );

        AiReplyDraftClientResult clientResult =
                new AiReplyDraftClientResult(
                        "Thanks for the update. Please try connecting again after restarting the VPN client.",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite"
                );

        AiReplyDraftResponse expectedResponse =
                new AiReplyDraftResponse(
                        "FD-000050",
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
                                "FD-000050"
                        )
        ).thenReturn(
                context
        );

        when(
                aiReplyDraftClient
                        .generateDraft(
                                any(
                                        AiReplyDraftClientRequest.class
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
                                "reply-draft-v3"
                        )
        ).thenReturn(
                expectedResponse
        );

        AiReplyDraftResponse response =
                service.generateDraft(
                        engineerId,
                        "FD-000050"
                );

        assertSame(
                expectedResponse,
                response
        );

        ArgumentCaptor<AiReplyDraftClientRequest>
                requestCaptor =
                ArgumentCaptor.forClass(
                        AiReplyDraftClientRequest.class
                );

        verify(
                aiReplyDraftClient
        ).generateDraft(
                requestCaptor.capture()
        );

        AiReplyDraftClientRequest request =
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
                "VPN unavailable",
                request.title()
        );

        assertEquals(
                "Users cannot connect to VPN.",
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
                "reply-draft-v3"
        );
    }

    @Test
    void providerFailureReturnsServiceUnavailableWithoutCompletion() {

        UUID engineerId =
                UUID.randomUUID();

        AiReplyDraftGenerationContext context =
                createContext(
                        UUID.randomUUID(),
                        "FD-000051"
                );

        when(
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000051"
                        )
        ).thenReturn(
                context
        );

        when(
                aiReplyDraftClient
                        .generateDraft(
                                any(
                                        AiReplyDraftClientRequest.class
                                )
                        )
        ).thenThrow(
                new AiReplyDraftClientException(
                        "Provider timeout"
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                service.generateDraft(
                                        engineerId,
                                        "FD-000051"
                                )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(
                "AI reply drafting is temporarily unavailable",
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

        AiReplyDraftGenerationContext context =
                createContext(
                        UUID.randomUUID(),
                        "FD-000052"
                );

        when(
                contextService
                        .prepareGeneration(
                                engineerId,
                                "FD-000052"
                        )
        ).thenReturn(
                context
        );

        when(
                aiReplyDraftClient
                        .generateDraft(
                                any(
                                        AiReplyDraftClientRequest.class
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
                                        "FD-000052"
                                )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                completionService
        );

        verify(
                contextService
        ).prepareGeneration(
                engineerId,
                "FD-000052"
        );

        verify(
                aiReplyDraftClient
        ).generateDraft(
                any(
                        AiReplyDraftClientRequest.class
                )
        );

        verifyNoMoreInteractions(
                contextService,
                aiReplyDraftClient
        );
    }

    private AiReplyDraftGenerationContext
            createContext(
                    UUID ticketId,
                    String ticketNumber
            ) {

        return new AiReplyDraftGenerationContext(
                ticketId,
                ticketNumber,
                TicketType.INCIDENT,
                TicketPriority.MEDIUM,
                TicketStatus.ASSIGNED,
                "Printer unavailable",
                "The office printer is unavailable.",
                List.of()
        );
    }
}