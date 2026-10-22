package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiSupportCopilotClient;
import com.flowdesk.ai.client.AiSupportCopilotClientException;
import com.flowdesk.ai.client.AiSupportCopilotClientRequest;
import com.flowdesk.ai.client.AiSupportCopilotClientResult;
import com.flowdesk.ai.client.AiSupportCopilotToolExecutor;

import com.flowdesk.ai.dto.AiSupportCopilotRequest;
import com.flowdesk.ai.dto.AiSupportCopilotResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AiSupportCopilotService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AiSupportCopilotService.class
            );

    private static final String PROMPT_VERSION =
            "support-copilot-v1";

    private final AiSupportCopilotClient
            aiSupportCopilotClient;

    private final AiSupportCopilotAccessService
            accessService;

    private final AiSupportCopilotToolDispatcher
            toolDispatcher;

    private final AiSupportCopilotCompletionService
            completionService;

    public AiSupportCopilotService(
            AiSupportCopilotClient aiSupportCopilotClient,
            AiSupportCopilotAccessService accessService,
            AiSupportCopilotToolDispatcher toolDispatcher,
            AiSupportCopilotCompletionService completionService
    ) {
        this.aiSupportCopilotClient =
                aiSupportCopilotClient;

        this.accessService =
                accessService;

        this.toolDispatcher =
                toolDispatcher;

        this.completionService =
                completionService;
    }

    /*
     * Intentionally NOT @Transactional.
     *
     * The complete Copilot run may contain several
     * external Gemini calls and several short
     * read-only tool executions.
     *
     * No database transaction should remain open
     * while waiting for the external AI provider.
     */
    public AiSupportCopilotResponse ask(
            UUID authenticatedUserId,
            String ticketNumber,
            AiSupportCopilotRequest request
    ) {

        if (request == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Copilot request is required"
            );
        }

        AiSupportCopilotClientRequest
                clientRequest;

        try {

            clientRequest =
                    new AiSupportCopilotClientRequest(
                            request.question()
                    );
        }
        catch (IllegalArgumentException exception) {

            /*
             * The controller will also use @Valid,
             * but keep service-level validation for
             * defense in depth.
             */
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }

        /*
         * Initial authorization check.
         *
         * If the engineer does not currently own the
         * ticket, we stop before spending an AI call.
         */
        AiSupportCopilotRunContext context =
                accessService
                        .prepareRun(
                                authenticatedUserId,
                                ticketNumber
                        );

        /*
         * The executor is permanently bound to:
         *
         * - authenticated engineer
         * - normalized ticket number
         *
         * Gemini itself cannot choose either value.
         */
        AiSupportCopilotToolExecutor toolExecutor =
                toolDispatcher
                        .createExecutor(
                                authenticatedUserId,
                                context.ticketNumber()
                        );

        AiSupportCopilotClientResult result;

        try {

            /*
             * External AI orchestration.
             *
             * This may perform:
             *
             * Gemini
             * -> tool request
             * -> short read-only DB call
             * -> Gemini
             * -> another tool request
             * -> ...
             * -> final answer
             *
             * AiSupportCopilotService itself owns no
             * database transaction.
             */
            result =
                    aiSupportCopilotClient
                            .run(
                                    clientRequest,
                                    toolExecutor
                            );
        }
        catch (
                AiSupportCopilotClientException exception
        ) {

            /*
             * Never log:
             *
             * - engineer question
             * - tool results
             * - ticket description
             * - conversation
             * - provider response body
             * - API key
             */
            LOGGER.warn(
                    "AI Support Copilot failed for ticket {}",
                    context.ticketNumber()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI Support Copilot is temporarily unavailable"
            );
        }

        if (result == null) {

            LOGGER.warn(
                    "AI Support Copilot provider returned no result for ticket {}",
                    context.ticketNumber()
            );

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI Support Copilot is temporarily unavailable"
            );
        }

        /*
         * Final short transaction.
         *
         * Re-check current ownership and only then:
         *
         * - write controlled audit metadata
         * - return the temporary Copilot answer
         */
        return completionService
                .completeRun(
                        authenticatedUserId,
                        context.ticketId(),
                        result,
                        PROMPT_VERSION
                );
    }
}