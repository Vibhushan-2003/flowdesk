package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiReplyDraftClient;
import com.flowdesk.ai.client.AiReplyDraftClientException;
import com.flowdesk.ai.client.AiReplyDraftClientRequest;
import com.flowdesk.ai.client.AiReplyDraftClientResult;

import com.flowdesk.ai.dto.AiReplyDraftResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AiReplyDraftService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AiReplyDraftService.class
            );

    private static final String PROMPT_VERSION =
            "reply-draft-v3";

    private final AiReplyDraftClient
            aiReplyDraftClient;

    private final AiReplyDraftContextService
            contextService;

    private final AiReplyDraftCompletionService
            completionService;

    public AiReplyDraftService(
            AiReplyDraftClient aiReplyDraftClient,
            AiReplyDraftContextService contextService,
            AiReplyDraftCompletionService completionService
    ) {
        this.aiReplyDraftClient =
                aiReplyDraftClient;

        this.contextService =
                contextService;

        this.completionService =
                completionService;
    }

    public AiReplyDraftResponse generateDraft(
            UUID authenticatedUserId,
            String ticketNumber
    ) {

        /*
         * Short read-only database transaction.
         *
         * Validates assignment and builds a privacy-safe
         * AI context.
         */
        AiReplyDraftGenerationContext context =
                contextService
                        .prepareGeneration(
                                authenticatedUserId,
                                ticketNumber
                        );

        AiReplyDraftClientRequest request =
                new AiReplyDraftClientRequest(
                        context.type(),
                        context.priority(),
                        context.status(),
                        context.title(),
                        context.description(),
                        context.conversation()
                );

        AiReplyDraftClientResult result;

        try {

            /*
             * External network call.
             *
             * There is deliberately no database
             * transaction around this call.
             */
            result =
                    aiReplyDraftClient
                            .generateDraft(
                                    request
                            );
        }
        catch (
                AiReplyDraftClientException exception
        ) {

            /*
             * Log only controlled provider details.
             *
             * Never log:
             * - prompt
             * - conversation
             * - ticket description
             * - API key
             * - provider response body
             */
            LOGGER.warn(
                    "AI reply drafting failed for ticket {}: {}",
                    context.ticketNumber(),
                    exception.getMessage()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI reply drafting is temporarily unavailable"
            );
        }

        if (result == null) {

            LOGGER.warn(
                    "AI reply drafting provider returned null for ticket {}",
                    context.ticketNumber()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI reply drafting is temporarily unavailable"
            );
        }

        /*
         * Second short database transaction.
         *
         * Re-check assignment/status after the network
         * call and write the controlled audit record.
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