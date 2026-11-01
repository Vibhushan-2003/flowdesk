package com.flowdesk.kb.domain;

public enum ArticleStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED;

    public boolean canTransitionTo(ArticleStatus target) {
        if (target == null || this == target) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == PUBLISHED || target == ARCHIVED;
            case PUBLISHED -> target == ARCHIVED || target == DRAFT;
            case ARCHIVED -> target == PUBLISHED || target == DRAFT;
        };
    }
}
