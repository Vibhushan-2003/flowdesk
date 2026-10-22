package com.flowdesk.ai.service;

import java.util.UUID;

public record AiSupportCopilotRunContext(

        UUID ticketId,

        String ticketNumber

) {
}