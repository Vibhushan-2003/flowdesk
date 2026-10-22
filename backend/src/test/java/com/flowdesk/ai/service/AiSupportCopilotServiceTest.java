package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiSupportCopilotClient;
import com.flowdesk.ai.client.AiSupportCopilotClientException;
import com.flowdesk.ai.client.AiSupportCopilotClientRequest;
import com.flowdesk.ai.client.AiSupportCopilotClientResult;
import com.flowdesk.ai.client.AiSupportCopilotToolExecutor;

import com.flowdesk.ai.dto.AiSupportCopilotRequest;
import com.flowdesk.ai.dto.AiSupportCopilotResponse;

import com.flowdesk.ai.tool.AiSupportCopilotTool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupportCopilotServiceTest {

    @Mock
    private AiSupportCopilotClient
            aiSupportCopilotClient;

    @Mock
    private AiSupportCopilotAccessService
            accessService;

    @Mock
    private AiSupportCopilotToolDispatcher
            toolDispatcher;

    @Mock
    private AiSupportCopilotCompletionService
            completionService;

    private AiSupportCopilotService
            copilotService;

    @BeforeEach
    void setUp() {

        copilotService =
                new AiSupportCopilotService(
                        aiSupportCopilotClient,
                        accessService,
                        toolDispatcher,
                        completionService
                );
    }

    @Test
    void askBindsAuthorizedTicketRunsClientAndCompletesResult() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        AiSupportCopilotRunContext context =
                new AiSupportCopilotRunContext(
                        ticketId,
                        "FD-000090"
                );

        AiSupportCopilotToolExecutor executor =
                tool -> "safe-result";

        AiSupportCopilotClientResult clientResult =
                new AiSupportCopilotClientResult(
                        "Focus on the resolution SLA first.",
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite",
                        List.of(
                                AiSupportCopilotTool
                                        .GET_SLA_SNAPSHOT
                        )
                );

        AiSupportCopilotResponse expectedResponse =
                new AiSupportCopilotResponse(
                        "FD-000090",
                        "Focus on the resolution SLA first.",
                        List.of(
                                AiSupportCopilotTool
                                        .GET_SLA_SNAPSHOT
                        ),
                        "GOOGLE_GEMINI",
                        "gemini-3.1-flash-lite",
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        )
                );

        when(
                accessService
                        .prepareRun(
                                engineerId,
                                "fd-000090"
                        )
        ).thenReturn(
                context
        );

        when(
                toolDispatcher
                        .createExecutor(
                                engineerId,
                                "FD-000090"
                        )
        ).thenReturn(
                executor
        );

        when(
                aiSupportCopilotClient
                        .run(
                                any(
                                        AiSupportCopilotClientRequest.class
                                ),
                                org.mockito.ArgumentMatchers.same(
                                        executor
                                )
                        )
        ).thenReturn(
                clientResult
        );

        when(
                completionService
                        .completeRun(
                                engineerId,
                                ticketId,
                                clientResult,
                                "support-copilot-v1"
                        )
        ).thenReturn(
                expectedResponse
        );

        AiSupportCopilotResponse actual =
                copilotService
                        .ask(
                                engineerId,
                                "fd-000090",
                                new AiSupportCopilotRequest(
                                        "  What should I focus on next?  "
                                )
                        );

        assertSame(
                expectedResponse,
                actual
        );

        verify(
                accessService
        ).prepareRun(
                engineerId,
                "fd-000090"
        );

        verify(
                toolDispatcher
        ).createExecutor(
                engineerId,
                "FD-000090"
        );

        verify(
                aiSupportCopilotClient
        ).run(
                new AiSupportCopilotClientRequest(
                        "What should I focus on next?"
                ),
                executor
        );

        verify(
                completionService
        ).completeRun(
                engineerId,
                ticketId,
                clientResult,
                "support-copilot-v1"
        );
    }

    @Test
    void unauthorizedTicketStopsBeforeProviderCall() {

        UUID engineerId =
                UUID.randomUUID();

        when(
                accessService
                        .prepareRun(
                                engineerId,
                                "FD-000091"
                        )
        ).thenThrow(
                new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Ticket not found or it is no longer assigned to you"
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                copilotService
                                        .ask(
                                                engineerId,
                                                "FD-000091",
                                                new AiSupportCopilotRequest(
                                                        "What should I do?"
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                toolDispatcher,
                aiSupportCopilotClient,
                completionService
        );
    }

    @Test
    void providerFailureReturnsControlledServiceUnavailable() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        AiSupportCopilotRunContext context =
                new AiSupportCopilotRunContext(
                        ticketId,
                        "FD-000092"
                );

        AiSupportCopilotToolExecutor executor =
                tool -> "safe-result";

        when(
                accessService
                        .prepareRun(
                                engineerId,
                                "FD-000092"
                        )
        ).thenReturn(
                context
        );

        when(
                toolDispatcher
                        .createExecutor(
                                engineerId,
                                "FD-000092"
                        )
        ).thenReturn(
                executor
        );

        when(
                aiSupportCopilotClient
                        .run(
                                any(
                                        AiSupportCopilotClientRequest.class
                                ),
                                org.mockito.ArgumentMatchers.same(
                                        executor
                                )
                        )
        ).thenThrow(
                new AiSupportCopilotClientException(
                        "Provider unavailable"
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                copilotService
                                        .ask(
                                                engineerId,
                                                "FD-000092",
                                                new AiSupportCopilotRequest(
                                                        "What is urgent?"
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(
                "AI Support Copilot is temporarily unavailable",
                exception.getReason()
        );

        verifyNoInteractions(
                completionService
        );
    }

    @Test
    void nullProviderResultReturnsControlledServiceUnavailable() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        AiSupportCopilotRunContext context =
                new AiSupportCopilotRunContext(
                        ticketId,
                        "FD-000093"
                );

        AiSupportCopilotToolExecutor executor =
                tool -> "safe-result";

        when(
                accessService
                        .prepareRun(
                                engineerId,
                                "FD-000093"
                        )
        ).thenReturn(
                context
        );

        when(
                toolDispatcher
                        .createExecutor(
                                engineerId,
                                "FD-000093"
                        )
        ).thenReturn(
                executor
        );

        when(
                aiSupportCopilotClient
                        .run(
                                any(
                                        AiSupportCopilotClientRequest.class
                                ),
                                org.mockito.ArgumentMatchers.same(
                                        executor
                                )
                        )
        ).thenReturn(
                null
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                copilotService
                                        .ask(
                                                engineerId,
                                                "FD-000093",
                                                new AiSupportCopilotRequest(
                                                        "Help me prioritize this."
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                completionService
        );
    }

    @Test
    void invalidQuestionStopsBeforeAuthorizationAndProvider() {

        UUID engineerId =
                UUID.randomUUID();

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                copilotService
                                        .ask(
                                                engineerId,
                                                "FD-000094",
                                                new AiSupportCopilotRequest(
                                                        "   "
                                                )
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        verifyNoInteractions(
                accessService,
                toolDispatcher,
                aiSupportCopilotClient,
                completionService
        );
    }

    @Test
    void nullRequestStopsBeforeAuthorizationAndProvider() {

        UUID engineerId =
                UUID.randomUUID();

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                copilotService
                                        .ask(
                                                engineerId,
                                                "FD-000095",
                                                null
                                        )
                );

        assertEquals(
                HttpStatus.BAD_REQUEST,
                exception.getStatusCode()
        );

        assertEquals(
                "Copilot request is required",
                exception.getReason()
        );

        verifyNoInteractions(
                accessService,
                toolDispatcher,
                aiSupportCopilotClient,
                completionService
        );
    }
}