package com.flowdesk.kb.dto;

import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.domain.KbArticle;

import java.time.Instant;
import java.util.UUID;

public record KbArticleSummaryResponse(
        UUID id,
        String title,
        String slug,
        String summary,
        ArticleStatus status,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        UUID authorId,
        String authorName,
        int viewCount,
        Instant publishedAt,
        Instant updatedAt
) {
    public static KbArticleSummaryResponse from(KbArticle article) {
        String authorName = article.getAuthor() != null
                ? article.getAuthor().getFirstName() + " " + article.getAuthor().getLastName()
                : "IT Support";

        return new KbArticleSummaryResponse(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getSummary(),
                article.getStatus(),
                article.getCategory().getId(),
                article.getCategory().getName(),
                article.getCategory().getSlug(),
                article.getAuthor() != null ? article.getAuthor().getId() : null,
                authorName,
                article.getViewCount(),
                article.getPublishedAt(),
                article.getUpdatedAt()
        );
    }
}
