package com.flowdesk.ai.dto;

import java.time.OffsetDateTime;

public record AiResolutionSummaryResponse(

        String ticketNumber,

        String draft,

        String provider,

        String model,

        OffsetDateTime generatedAt

) {
}