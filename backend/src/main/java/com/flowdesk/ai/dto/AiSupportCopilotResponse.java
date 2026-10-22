package com.flowdesk.ai.dto;

import com.flowdesk.ai.tool.AiSupportCopilotTool;

import java.time.OffsetDateTime;

import java.util.List;

public record AiSupportCopilotResponse(

        String ticketNumber,

        String answer,

        List<AiSupportCopilotTool> toolsUsed,

        String provider,

        String model,

        OffsetDateTime generatedAt

) {

    public AiSupportCopilotResponse {

        toolsUsed =
                List.copyOf(
                        toolsUsed
                );
    }
}