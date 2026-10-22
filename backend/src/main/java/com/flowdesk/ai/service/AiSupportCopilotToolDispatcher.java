package com.flowdesk.ai.service;

import com.flowdesk.ai.client.AiSupportCopilotClientException;
import com.flowdesk.ai.client.AiSupportCopilotToolExecutor;

import com.flowdesk.ai.tool.AiSupportCopilotTool;
import com.flowdesk.ai.tool.AiSupportCopilotToolService;

import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class AiSupportCopilotToolDispatcher {

    private final AiSupportCopilotToolService
            toolService;

    public AiSupportCopilotToolDispatcher(
            AiSupportCopilotToolService toolService
    ) {
        this.toolService =
                toolService;
    }

    /*
     * Creates an executor that is permanently bound
     * to one authenticated engineer and one ticket.
     *
     * Gemini receives no engineer id and no ticket
     * number as tool arguments.
     */
    public AiSupportCopilotToolExecutor
            createExecutor(
                    UUID authenticatedUserId,
                    String ticketNumber
            ) {

        Objects.requireNonNull(
                authenticatedUserId,
                "Authenticated user id is required"
        );

        if (ticketNumber == null
                || ticketNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "Ticket number is required"
            );
        }

        String normalizedTicketNumber =
                ticketNumber.trim();

        return tool ->
                execute(
                        authenticatedUserId,
                        normalizedTicketNumber,
                        tool
                );
    }

    private Object execute(
            UUID authenticatedUserId,
            String ticketNumber,
            AiSupportCopilotTool tool
    ) {

        if (tool == null) {

            throw new AiSupportCopilotClientException(
                    "AI provider requested an invalid Copilot tool"
            );
        }

        /*
         * This switch is the capability allowlist.
         *
         * There is no reflection, dynamic method name,
         * SQL access or arbitrary command execution.
         *
         * The agent can only reach these three
         * explicitly approved read-only operations.
         */
        return switch (tool) {

            case GET_TICKET_OVERVIEW ->
                    toolService
                            .getTicketOverview(
                                    authenticatedUserId,
                                    ticketNumber
                            );

            case GET_PUBLIC_CONVERSATION ->
                    toolService
                            .getPublicConversation(
                                    authenticatedUserId,
                                    ticketNumber
                            );

            case GET_SLA_SNAPSHOT ->
                    toolService
                            .getSlaSnapshot(
                                    authenticatedUserId,
                                    ticketNumber
                            );
        };
    }
}