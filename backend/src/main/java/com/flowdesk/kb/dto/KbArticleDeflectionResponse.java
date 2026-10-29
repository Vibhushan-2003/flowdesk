package com.flowdesk.kb.dto;

import com.flowdesk.kb.domain.KbArticle;

import java.util.UUID;

public record KbArticleDeflectionResponse(
        UUID id,
        String title,
        String slug,
        String summary,
        String categoryName,
        String categorySlug
) {
    public static KbArticleDeflectionResponse from(KbArticle article) {
        return new KbArticleDeflectionResponse(
                article.getId(),
                article.getTitle(),
                article.getSlug(),
                article.getSummary(),
                article.getCategory().getName(),
                article.getCategory().getSlug()
        );
    }
}
