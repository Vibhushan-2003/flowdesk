package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiTriageClient;
import com.flowdesk.ai.client.AiTriageClientException;
import com.flowdesk.ai.client.AiTriageClientRequest;
import com.flowdesk.ai.client.AiTriageClientResult;

import com.flowdesk.ai.dto.AiTriageResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

@Service
public class AiTriageService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AiTriageService.class
            );

    private static final String PROMPT_VERSION =
            "triage-v1";

    private final AiTriageClient
            aiTriageClient;

    private final AiTriagePersistenceService
            persistenceService;

    public AiTriageService(
            AiTriageClient aiTriageClient,
            AiTriagePersistenceService persistenceService
    ) {
        this.aiTriageClient =
                aiTriageClient;

        this.persistenceService =
                persistenceService;
    }

    public AiTriageResponse generateRecommendation(
            UUID authenticatedUserId,
            String ticketNumber
    ) {

        /*
         * First short database transaction.
         *
         * Verify that:
         * - this ticket exists,
         * - it is actively assigned to this engineer,
         * - no AI recommendation already exists.
         *
         * The transaction finishes before we call
         * the external AI provider.
         */
        AiTriageGenerationContext context =
                persistenceService
                        .prepareGeneration(
                                authenticatedUserId,
                                ticketNumber
                        );

        AiTriageClientResult result;

        try {
            /*
             * External network call.
             *
             * This happens outside a database
             * transaction so a slow AI provider
             * cannot hold PostgreSQL resources.
             */
            result =
                    aiTriageClient.analyze(
                            new AiTriageClientRequest(
                                    context.type(),
                                    context.title(),
                                    context.description()
                            )
                    );
        }
        catch (AiTriageClientException exception) {

            /*
             * Log only our controlled provider-error
             * message.
             *
             * Do not log:
             * - API key
             * - provider response body
             * - ticket description
             */
            LOGGER.warn(
                    "AI triage failed for ticket {}: {}",
                    context.ticketNumber(),
                    exception.getMessage()
            );

            /*
             * External callers receive only a stable,
             * generic 503 response.
             *
             * Internal provider details must not be
             * exposed through the FlowDesk API.
             */
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI triage is temporarily unavailable"
            );
        }

        if (result == null) {

            LOGGER.warn(
                    "AI triage provider returned null for ticket {}",
                    context.ticketNumber()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI triage is temporarily unavailable"
            );
        }

        Objects.requireNonNull(
                authenticatedUserId,
                "Authenticated user id is required"
        );

        /*
         * Second short database transaction.
         *
         * The persistence service re-checks:
         * - active assignment
         * - current engineer
         * - duplicate recommendation
         *
         * Recommendation and audit log are then
         * written atomically.
         */
        return persistenceService
                .persistRecommendation(
                        authenticatedUserId,
                        context.ticketId(),
                        result,
                        PROMPT_VERSION
                );
    }

    public AiTriageResponse getRecommendation(
            UUID authenticatedUserId,
            String ticketNumber
    ) {

        return persistenceService
                .getRecommendation(
                        authenticatedUserId,
                        ticketNumber
                );
    }
}