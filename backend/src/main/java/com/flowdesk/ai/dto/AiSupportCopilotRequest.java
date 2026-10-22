package com.flowdesk.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiSupportCopilotRequest(

        @NotBlank(
                message = "Copilot question is required"
        )
        @Size(
                max = 1000,
                message = "Copilot question must not exceed 1000 characters"
        )
        String question

) {
}