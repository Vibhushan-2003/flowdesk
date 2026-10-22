package com.flowdesk.ai.client;

public interface AiResolutionSummaryClient {

    AiResolutionSummaryClientResult generateDraft(
            AiResolutionSummaryClientRequest request
    );
}