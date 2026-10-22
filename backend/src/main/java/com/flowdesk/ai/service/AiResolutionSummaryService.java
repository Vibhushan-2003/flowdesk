package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiResolutionSummaryClient;
import com.flowdesk.ai.client.AiResolutionSummaryClientException;
import com.flowdesk.ai.client.AiResolutionSummaryClientRequest;
import com.flowdesk.ai.client.AiResolutionSummaryClientResult;

import com.flowdesk.ai.dto.AiResolutionSummaryResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AiResolutionSummaryService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AiResolutionSummaryService.class
            );

    private static final String PROMPT_VERSION =
            "resolution-summary-v1";

    private final AiResolutionSummaryClient
            aiResolutionSummaryClient;

    private final AiResolutionSummaryContextService
            contextService;

    private final AiResolutionSummaryCompletionService
            completionService;

    public AiResolutionSummaryService(
            AiResolutionSummaryClient aiResolutionSummaryClient,
            AiResolutionSummaryContextService contextService,
            AiResolutionSummaryCompletionService completionService
    ) {
        this.aiResolutionSummaryClient =
                aiResolutionSummaryClient;

        this.contextService =
                contextService;

        this.completionService =
                completionService;
    }

    public AiResolutionSummaryResponse generateDraft(
            UUID authenticatedUserId,
            String ticketNumber
    ) {

        /*
         * First short read-only database transaction.
         *
         * The context service:
         * - validates active assignment
         * - requires IN_PROGRESS
         * - loads public conversation
         * - builds privacy-safe AI context
         */
        AiResolutionSummaryGenerationContext context =
                contextService
                        .prepareGeneration(
                                authenticatedUserId,
                                ticketNumber
                        );

        /*
         * Important privacy boundary.
         *
         * The AI-facing request does NOT contain:
         *
         * - ticket UUID
         * - requester UUID
         * - requester email
         * - support engineer UUID
         * - assignment id
         * - SLA timestamps
         * - audit data
         * - JWT
         * - API key
         *
         * Only the safe business context crosses the
         * provider boundary.
         */
        AiResolutionSummaryClientRequest request =
                new AiResolutionSummaryClientRequest(
                        context.type(),
                        context.priority(),
                        context.status(),
                        context.title(),
                        context.description(),
                        context.conversation()
                );

        AiResolutionSummaryClientResult result;

        try {

            /*
             * External AI network call.
             *
             * AiResolutionSummaryService itself has no
             * @Transactional annotation.
             *
             * The read transaction in contextService
             * has already ended before this call starts.
             *
             * Therefore we do NOT hold database locks or
             * transactions while waiting for Gemini.
             */
            result =
                    aiResolutionSummaryClient
                            .generateDraft(
                                    request
                            );
        }
        catch (
                AiResolutionSummaryClientException exception
        ) {

            /*
             * Keep logs deliberately minimal.
             *
             * Do not log:
             * - provider response body
             * - prompt
             * - ticket description
             * - conversation
             * - generated text
             * - API key
             */
            LOGGER.warn(
                    "AI resolution summary generation failed for ticket {}",
                    context.ticketNumber()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI resolution summary drafting is temporarily unavailable"
            );
        }

        if (result == null) {

            LOGGER.warn(
                    "AI resolution summary provider returned no result for ticket {}",
                    context.ticketNumber()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI resolution summary drafting is temporarily unavailable"
            );
        }

        /*
         * Second short database transaction.
         *
         * The completion service re-checks:
         *
         * - active assignment still exists
         * - same engineer still owns it
         * - ticket is still IN_PROGRESS
         *
         * Only then is controlled audit metadata written.
         */
        return completionService
                .completeGeneration(
                        authenticatedUserId,
                        context.ticketId(),
                        result,
                        PROMPT_VERSION
                );
    }
}