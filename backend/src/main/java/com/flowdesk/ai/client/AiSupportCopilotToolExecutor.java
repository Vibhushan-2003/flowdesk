package com.flowdesk.ai.client;

import com.flowdesk.ai.tool.AiSupportCopilotTool;

@FunctionalInterface
public interface AiSupportCopilotToolExecutor {

    Object execute(
            AiSupportCopilotTool tool
    );
}