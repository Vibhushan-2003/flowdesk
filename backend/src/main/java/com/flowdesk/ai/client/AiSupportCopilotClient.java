package com.flowdesk.ai.client;

public interface AiSupportCopilotClient {

    AiSupportCopilotClientResult run(
            AiSupportCopilotClientRequest request,
            AiSupportCopilotToolExecutor toolExecutor
    );
}