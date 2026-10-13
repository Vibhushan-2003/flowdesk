package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiTriageClient;
import com.flowdesk.ai.client.AiTriageClientException;
import com.flowdesk.ai.client.AiTriageClientRequest;
import com.flowdesk.ai.client.AiTriageClientResult;

import com.flowdesk.ai.dto.AiTriageResponse;

import com.flowdesk.ticket.domain.TicketPriority;
import com.flowdesk.ticket.domain.TicketType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;

import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTriageServiceTest {

    @Mock
    private AiTriageClient
            aiTriageClient;

    @Mock
    private AiTriagePersistenceService
            persistenceService;

    private AiTriageService
            aiTriageService;

    @BeforeEach
    void setUp() {

        aiTriageService =
                new AiTriageService(
                        aiTriageClient,
                        persistenceService
                );
    }

    @Test
    void generateRecommendationUsesSafeTicketDataAndPersistsResult() {

        UUID engineerId =
                UUID.randomUUID();

        UUID ticketId =
                UUID.randomUUID();

        AiTriageGenerationContext context =
                new AiTriageGenerationContext(
                        ticketId,
                        "FD-000025",
                        TicketType.INCIDENT,
                        "VPN unavailable",
                        "Employee cannot connect to the company VPN."
                );

        AiTriageClientResult clientResult =
                new AiTriageClientResult(
                        TicketPriority.HIGH,
                        new BigDecimal(
                                "0.87"
                        ),
                        "Important work is blocked.",
                        "GOOGLE_GEMINI",
                        "gemini-3.8-flash"
                );

        AiTriageResponse expectedResponse =
                new AiTriageResponse(
                        UUID.randomUUID(),
                        ticketId,
                        "FD-000025",
                        TicketPriority.HIGH,
                        new BigDecimal(
                                "0.8700"
                        ),
                        "Important work is blocked.",
                        "GOOGLE_GEMINI",
                        "gemini-3.8-flash",
                        "triage-v1",
                        engineerId,
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        )
                );

        when(
                persistenceService
                        .prepareGeneration(
                                engineerId,
                                "FD-000025"
                        )
        ).thenReturn(
                context
        );

        when(
                aiTriageClient.analyze(
                        org.mockito.ArgumentMatchers
                                .any(
                                        AiTriageClientRequest.class
                                )
                )
        ).thenReturn(
                clientResult
        );

        when(
                persistenceService
                        .persistRecommendation(
                                engineerId,
                                ticketId,
                                clientResult,
                                "triage-v1"
                        )
        ).thenReturn(
                expectedResponse
        );

        AiTriageResponse response =
                aiTriageService
                        .generateRecommendation(
                                engineerId,
                                "FD-000025"
                        );

        assertSame(
                expectedResponse,
                response
        );

        ArgumentCaptor<AiTriageClientRequest>
                requestCaptor =
                ArgumentCaptor.forClass(
                        AiTriageClientRequest.class
                );

        verify(
                aiTriageClient
        ).analyze(
                requestCaptor.capture()
        );

        AiTriageClientRequest sentRequest =
                requestCaptor.getValue();

        assertEquals(
                TicketType.INCIDENT,
                sentRequest.type()
        );

        assertEquals(
                "VPN unavailable",
                sentRequest.title()
        );

        assertEquals(
                "Employee cannot connect to the company VPN.",
                sentRequest.description()
        );

        verify(
                persistenceService
        ).persistRecommendation(
                engineerId,
                ticketId,
                clientResult,
                "triage-v1"
        );
    }

    @Test
    void providerFailureReturnsServiceUnavailableAndDoesNotPersist() {

        UUID engineerId =
                UUID.randomUUID();

        AiTriageGenerationContext context =
                new AiTriageGenerationContext(
                        UUID.randomUUID(),
                        "FD-000026",
                        TicketType.INCIDENT,
                        "Email unavailable",
                        "User cannot access email."
                );

        when(
                persistenceService
                        .prepareGeneration(
                                engineerId,
                                "FD-000026"
                        )
        ).thenReturn(
                context
        );

        when(
                aiTriageClient.analyze(
                        org.mockito.ArgumentMatchers
                                .any(
                                        AiTriageClientRequest.class
                                )
                )
        ).thenThrow(
                new AiTriageClientException(
                        "Provider timeout"
                )
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                aiTriageService
                                        .generateRecommendation(
                                                engineerId,
                                                "FD-000026"
                                        )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        assertEquals(
                "AI triage is temporarily unavailable",
                exception.getReason()
        );

        verifyNoInteractionsAfterPreparation();
    }

    @Test
    void nullProviderResultReturnsServiceUnavailable() {

        UUID engineerId =
                UUID.randomUUID();

        AiTriageGenerationContext context =
                new AiTriageGenerationContext(
                        UUID.randomUUID(),
                        "FD-000027",
                        TicketType.SERVICE_REQUEST,
                        "Install approved software",
                        "Please install the approved reporting tool."
                );

        when(
                persistenceService
                        .prepareGeneration(
                                engineerId,
                                "FD-000027"
                        )
        ).thenReturn(
                context
        );

        when(
                aiTriageClient.analyze(
                        org.mockito.ArgumentMatchers
                                .any(
                                        AiTriageClientRequest.class
                                )
                )
        ).thenReturn(
                null
        );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                aiTriageService
                                        .generateRecommendation(
                                                engineerId,
                                                "FD-000027"
                                        )
                );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );

        verify(
                persistenceService
        ).prepareGeneration(
                engineerId,
                "FD-000027"
        );

        verify(
                aiTriageClient
        ).analyze(
                org.mockito.ArgumentMatchers
                        .any(
                                AiTriageClientRequest.class
                        )
        );

        org.mockito.Mockito.verifyNoMoreInteractions(
                persistenceService
        );
    }

    @Test
    void getRecommendationDelegatesToPersistenceService() {

        UUID engineerId =
                UUID.randomUUID();

        AiTriageResponse expected =
                new AiTriageResponse(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "FD-000028",
                        TicketPriority.MEDIUM,
                        new BigDecimal(
                                "0.8000"
                        ),
                        "Normal user impact.",
                        "GOOGLE_GEMINI",
                        "gemini-3.8-flash",
                        "triage-v1",
                        engineerId,
                        OffsetDateTime.now(
                                ZoneOffset.UTC
                        )
                );

        when(
                persistenceService
                        .getRecommendation(
                                engineerId,
                                "FD-000028"
                        )
        ).thenReturn(
                expected
        );

        AiTriageResponse response =
                aiTriageService
                        .getRecommendation(
                                engineerId,
                                "FD-000028"
                        );

        assertSame(
                expected,
                response
        );

        verify(
                persistenceService
        ).getRecommendation(
                engineerId,
                "FD-000028"
        );

        verifyNoInteractions(
                aiTriageClient
        );
    }

    private void verifyNoInteractionsAfterPreparation() {

        verify(
                persistenceService
        ).prepareGeneration(
                org.mockito.ArgumentMatchers
                        .any(UUID.class),
                org.mockito.ArgumentMatchers
                        .anyString()
        );

        org.mockito.Mockito
                .verifyNoMoreInteractions(
                        persistenceService
                );
    }
}