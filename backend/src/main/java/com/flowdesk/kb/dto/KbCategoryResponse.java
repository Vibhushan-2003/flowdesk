package com.flowdesk.kb.dto;

import com.flowdesk.kb.domain.KbCategory;

import java.util.UUID;

public record KbCategoryResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String icon,
        int displayOrder,
        boolean active,
        long articleCount
) {
    public static KbCategoryResponse from(KbCategory category, long articleCount) {
        return new KbCategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getIcon(),
                category.getDisplayOrder(),
                category.isActive(),
                articleCount
        );
    }
}
