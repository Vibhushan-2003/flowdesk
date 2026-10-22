package com.flowdesk.ai.client;

import com.flowdesk.ai.tool.AiSupportCopilotTool;

import java.util.List;

public record AiSupportCopilotClientResult(

        String answer,

        String provider,

        String model,

        List<AiSupportCopilotTool> toolsUsed

) {

    public AiSupportCopilotClientResult {

        if (answer == null
                || answer.isBlank()) {

            throw new IllegalArgumentException(
                    "Copilot answer is required"
            );
        }

        if (provider == null
                || provider.isBlank()) {

            throw new IllegalArgumentException(
                    "AI provider is required"
            );
        }

        if (model == null
                || model.isBlank()) {

            throw new IllegalArgumentException(
                    "AI model is required"
            );
        }

        if (toolsUsed == null) {

            throw new IllegalArgumentException(
                    "Copilot tools used are required"
            );
        }

        answer =
                answer.trim();

        provider =
                provider.trim();

        model =
                model.trim();

        toolsUsed =
                List.copyOf(
                        toolsUsed
                );
    }
}