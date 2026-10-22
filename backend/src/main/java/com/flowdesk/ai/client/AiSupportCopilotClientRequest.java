package com.flowdesk.ai.client;

public record AiSupportCopilotClientRequest(

        String question

) {

    private static final int MAX_QUESTION_LENGTH =
            1000;

    public AiSupportCopilotClientRequest {

        if (question == null
                || question.isBlank()) {

            throw new IllegalArgumentException(
                    "Copilot question is required"
            );
        }

        question =
                question.trim();

        if (question.length()
                > MAX_QUESTION_LENGTH) {

            throw new IllegalArgumentException(
                    "Copilot question must not exceed 1000 characters"
            );
        }
    }
}