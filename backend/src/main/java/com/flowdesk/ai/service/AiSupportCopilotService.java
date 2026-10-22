
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiSupportCopilotService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    AiSupportCopilotService.class
            );

    private static final String PROMPT_VERSION =
            "support-copilot-v1";

    /*
     * Match only the controlled error message
     * created by our own Gemini client.
     *
     * Never print raw provider response bodies,
     * exception stack traces or sensitive content.
     */
    private static final Pattern PROVIDER_STATUS_PATTERN =
            Pattern.compile(
                    "^AI provider rejected the Copilot request with status (\\d{3})$"
            );

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

        AiSupportCopilotClientRequest clientRequest;

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
         * If the engineer does not currently own
         * the ticket, we stop before spending an
         * external AI call.
         */
        AiSupportCopilotRunContext context =
                accessService.prepareRun(
                        authenticatedUserId,
                        ticketNumber
                );

        /*
         * The executor is permanently bound to:
         *
         * - authenticated engineer
         * - normalized ticket number
         *
         * Gemini cannot choose either value.
         */
        AiSupportCopilotToolExecutor toolExecutor =
                toolDispatcher.createExecutor(
                        authenticatedUserId,
                        context.ticketNumber()
                );

        AiSupportCopilotClientResult result;

        try {

            /*
             * External AI orchestration.
             *
             * Gemini
             * -> tool request
             * -> short read-only DB call
             * -> Gemini
             * -> final answer
             *
             * No database transaction stays open
             * while waiting for Gemini.
             */
            result =
                    aiSupportCopilotClient.run(
                            clientRequest,
                            toolExecutor
                    );
        }
        catch (AiSupportCopilotClientException exception) {

            /*
             * Log only a controlled error category.
             *
             * Do NOT log:
             * - exception objects or stack traces
             * - raw exception messages
             * - API keys
             * - provider response bodies
             * - ticket descriptions or conversations
             * - engineer questions or tool results
             */
            LOGGER.warn(
                    "AI Support Copilot failed for ticket {}. failureCategory={}",
                    context.ticketNumber(),
                    classifyFailure(exception)
            );

            /*
             * Preserve the existing safe response.
             *
             * The frontend must not receive
             * internal provider error details.
             */
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
        return completionService.completeRun(
                authenticatedUserId,
                context.ticketId(),
                result,
                PROMPT_VERSION
        );
    }

    /*
     * Convert known internal client failures
     * into safe diagnostic categories.
     *
     * The raw message is inspected internally
     * but is never written to application logs.
     */
    private String classifyFailure(
            AiSupportCopilotClientException exception
    ) {

        String message = exception.getMessage();

        if (message == null) {
            return "UNKNOWN_CLIENT_FAILURE";
        }

        Matcher matcher =
                PROVIDER_STATUS_PATTERN.matcher(message);

        if (matcher.matches()) {

            int status =
                    Integer.parseInt(matcher.group(1));

            if (status == 429) {
                return "PROVIDER_RATE_LIMITED";
            }

            if (status == 401 || status == 403) {
                return "PROVIDER_AUTH_FAILURE";
            }

            if (status == 400 || status == 404 || status == 422) {
                return "PROVIDER_REQUEST_REJECTED";
            }

            if (status >= 500 && status <= 599) {
                return "PROVIDER_SERVER_ERROR";
            }

            return "PROVIDER_HTTP_ERROR";
        }

        if (message.equals(
                "AI Support Copilot is not configured"
        )) {
            return "PROVIDER_NOT_CONFIGURED";
        }

        if (message.equals(
                "AI provider is currently unavailable"
        )) {
            return "PROVIDER_CONNECTION_FAILURE";
        }

        if (message.equals(
                "AI provider exceeded the Copilot tool-call limit"
        )) {
            return "TOOL_CALL_LIMIT_EXCEEDED";
        }

        if (message.equals(
                "Copilot tool returned no result"
        )) {
            return "TOOL_RESULT_MISSING";
        }

        if (message.equals(
                "Copilot tool result could not be serialized"
        )) {
            return "TOOL_SERIALIZATION_FAILURE";
        }

        if (message.equals(
                "AI provider returned no Copilot response"
        ) || message.equals(
                "AI provider returned no Copilot answer"
        ) || message.equals(
                "AI provider returned an invalid Copilot result"
        ) || message.equals(
                "AI provider returned no Copilot interaction status"
        ) || message.equals(
                "AI provider returned no Copilot interaction steps"
        ) || message.equals(
                "AI provider did not complete the Copilot interaction"
        ) || message.equals(
                "AI provider returned function calls in an invalid interaction state"
        ) || message.equals(
                "AI provider returned invalid Copilot tool arguments"
        ) || message.equals(
                "AI provider requested an unsupported Copilot tool"
        ) || message.equals(
                "AI provider supplied unexpected Copilot tool arguments"
        ) || message.equals(
                "AI provider returned a Copilot answer that is too long"
        )) {
            return "INVALID_PROVIDER_RESPONSE";
        }

        return "UNKNOWN_CLIENT_FAILURE";
    }
}
