package com.flowdesk.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateKbArticleRequest(
        @NotNull(message = "Category ID is required")
        UUID categoryId,

        @NotBlank(message = "Title is required")
        @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
        String title,

        @NotBlank(message = "Summary is required")
        @Size(min = 5, max = 500, message = "Summary must be between 5 and 500 characters")
        String summary,

        @NotBlank(message = "Content is required")
        String contentMarkdown
) {}
