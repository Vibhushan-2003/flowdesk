package com.flowdesk.kb.dto;

import com.flowdesk.kb.domain.ArticleStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateKbArticleStatusRequest(
        @NotNull(message = "Target status is required")
        ArticleStatus status
) {}
