package com.flowdesk.kb.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditTargetType;
import com.flowdesk.audit.service.AuditService;
import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.domain.KbArticle;
import com.flowdesk.kb.domain.KbCategory;
import com.flowdesk.kb.dto.CreateKbArticleRequest;
import com.flowdesk.kb.dto.CreateKbCategoryRequest;
import com.flowdesk.kb.dto.KbArticleDeflectionResponse;
import com.flowdesk.kb.dto.KbArticleResponse;
import com.flowdesk.kb.dto.KbArticleSummaryResponse;
import com.flowdesk.kb.dto.KbCategoryResponse;
import com.flowdesk.kb.dto.UpdateKbArticleRequest;
import com.flowdesk.kb.dto.UpdateKbCategoryRequest;
import com.flowdesk.kb.repository.KbArticleRepository;
import com.flowdesk.kb.repository.KbCategoryRepository;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KbArticleRepository articleRepository;
    private final KbCategoryRepository categoryRepository;
    private final SlugService slugService;
    private final AuditService auditService;

    public KnowledgeBaseService(
            KbArticleRepository articleRepository,
            KbCategoryRepository categoryRepository,
            SlugService slugService,
            AuditService auditService
    ) {
        this.articleRepository = articleRepository;
        this.categoryRepository = categoryRepository;
        this.slugService = slugService;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------------
    // Employee / Public Read APIs
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<KbCategoryResponse> getActiveCategories() {
        List<KbCategory> categories = categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        return categories.stream()
                .map(cat -> {
                    long count = articleRepository.countByCategoryIdAndStatus(cat.getId(), ArticleStatus.PUBLISHED);
                    return KbCategoryResponse.from(cat, count);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<KbArticleSummaryResponse> searchPublishedArticles(
            UUID categoryId,
            String query,
            int page,
            int size
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 50);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);

        String trimmedQuery = query != null && !query.trim().isBlank() ? query.trim() : null;

        Page<KbArticle> articlePage = articleRepository.searchPublished(categoryId, trimmedQuery, pageable);

        List<KbArticleSummaryResponse> content = articlePage.getContent().stream()
                .map(KbArticleSummaryResponse::from)
                .toList();

        return new PageResponse<>(
                content,
                articlePage.getNumber(),
                articlePage.getSize(),
                articlePage.getTotalElements(),
                articlePage.getTotalPages(),
                articlePage.isFirst(),
                articlePage.isLast()
        );
    }

    @Transactional
    public KbArticleResponse getArticleBySlug(String slug, User currentUser) {
        KbArticle article = articleRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));

        if (article.getStatus() != ArticleStatus.PUBLISHED) {
            boolean hasStaffAccess = currentUser != null && currentUser.getRoles().stream()
                    .anyMatch(r -> r.getCode() == RoleCode.SUPPORT_ENGINEER ||
                            r.getCode() == RoleCode.TEAM_LEAD ||
                            r.getCode() == RoleCode.ADMIN);

            if (!hasStaffAccess) {
                // Security cloaking: unpublished articles return 404 to employees
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found");
            }
        }

        if (article.getStatus() == ArticleStatus.PUBLISHED) {
            article.incrementViewCount();
            articleRepository.save(article);
        }

        return KbArticleResponse.from(article);
    }

    @Transactional(readOnly = true)
    public List<KbArticleDeflectionResponse> getDeflectionSuggestions(String query) {
        if (query == null || query.trim().length() < 3) {
            return Collections.emptyList();
        }

        String trimmed = query.trim();
        List<KbArticle> articles = articleRepository.findDeflectionSuggestions(trimmed, 3);

        return articles.stream()
                .map(KbArticleDeflectionResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------------
    // Support Engineer / Team Lead / Admin Management APIs
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<KbArticleSummaryResponse> searchManagementArticles(
            ArticleStatus status,
            UUID categoryId,
            String query,
            int page,
            int size
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.clamp(size, 1, 50);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize);

        String statusStr = status != null ? status.name() : null;
        String trimmedQuery = query != null && !query.trim().isBlank() ? query.trim() : null;

        Page<KbArticle> articlePage = articleRepository.searchManagement(statusStr, categoryId, trimmedQuery, pageable);

        List<KbArticleSummaryResponse> content = articlePage.getContent().stream()
                .map(KbArticleSummaryResponse::from)
                .toList();

        return new PageResponse<>(
                content,
                articlePage.getNumber(),
                articlePage.getSize(),
                articlePage.getTotalElements(),
                articlePage.getTotalPages(),
                articlePage.isFirst(),
                articlePage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public KbArticleResponse getArticleByIdForManagement(UUID id) {
        KbArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));

        return KbArticleResponse.from(article);
    }

    @Transactional
    public KbArticleResponse createDraftArticle(CreateKbArticleRequest request, User author) {
        KbCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));

        String slug = slugService.generateUniqueSlug(request.title(), articleRepository::existsBySlug);

        KbArticle article = new KbArticle(
                category,
                request.title().trim(),
                slug,
                request.summary().trim(),
                request.contentMarkdown().trim(),
                author
        );

        KbArticle saved;
        try {
            saved = articleRepository.saveAndFlush(article);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Slug collision on save for title '{}', retrying with randomized slug", request.title());
            article.setSlug(slugService.toSlug(request.title()) + "-" + UUID.randomUUID().toString().substring(0, 8));
            saved = articleRepository.saveAndFlush(article);
        }

        auditService.recordUserAction(
                author,
                AuditAction.KB_ARTICLE_CREATED,
                AuditTargetType.KB_ARTICLE,
                saved.getId(),
                saved.getSlug(),
                Map.of(
                        "title", saved.getTitle(),
                        "category", category.getName(),
                        "status", saved.getStatus().name()
                )
        );

        return KbArticleResponse.from(saved);
    }

    @Transactional
    public KbArticleResponse updateArticle(UUID id, UpdateKbArticleRequest request, User user) {
        KbArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));

        KbCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found"));

        // If title changed meaningfully, generate an updated unique slug
        if (!article.getTitle().equalsIgnoreCase(request.title().trim())) {
            String newSlug = slugService.generateUniqueSlug(
                    request.title().trim(),
                    s -> articleRepository.existsBySlugAndIdNot(s, id)
            );
            article.setSlug(newSlug);
        }

        article.update(
                request.title().trim(),
                request.summary().trim(),
                request.contentMarkdown().trim(),
                category
        );

        KbArticle saved = articleRepository.save(article);

        auditService.recordUserAction(
                user,
                AuditAction.KB_ARTICLE_UPDATED,
                AuditTargetType.KB_ARTICLE,
                saved.getId(),
                saved.getSlug(),
                Map.of(
                        "title", saved.getTitle(),
                        "category", category.getName(),
                        "status", saved.getStatus().name()
                )
        );

        return KbArticleResponse.from(saved);
    }

    @Transactional
    public KbArticleResponse transitionStatus(UUID id, ArticleStatus targetStatus, User user) {
        KbArticle article = articleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found"));

        try {
            article.transitionStatus(targetStatus);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        KbArticle saved = articleRepository.save(article);

        AuditAction action = switch (targetStatus) {
            case PUBLISHED -> AuditAction.KB_ARTICLE_PUBLISHED;
            case ARCHIVED -> AuditAction.KB_ARTICLE_ARCHIVED;
            case DRAFT -> AuditAction.KB_ARTICLE_UPDATED;
        };

        auditService.recordUserAction(
                user,
                action,
                AuditTargetType.KB_ARTICLE,
                saved.getId(),
                saved.getSlug(),
                Map.of(
                        "title", saved.getTitle(),
                        "newStatus", targetStatus.name()
                )
        );

        return KbArticleResponse.from(saved);
    }

    // ------------------------------------------------------------------------
    // Category Management APIs (Team Lead / Admin)
    // ------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<KbCategoryResponse> getAllCategoriesForAdmin() {
        List<KbCategory> categories = categoryRepository.findAllByOrderByDisplayOrderAscNameAsc();
        return categories.stream()
                .map(cat -> {
                    long count = articleRepository.countByCategoryId(cat.getId());
                    return KbCategoryResponse.from(cat, count);
                })
                .toList();
    }

    @Transactional
    public KbCategoryResponse createCategory(CreateKbCategoryRequest request, User user) {
        String slug = slugService.generateUniqueSlug(request.name(), categoryRepository::existsBySlug);
        int order = request.displayOrder() != null ? request.displayOrder() : 0;
        String icon = request.icon() != null && !request.icon().isBlank() ? request.icon() : "folder";

        KbCategory category = new KbCategory(
                request.name().trim(),
                slug,
                request.description() != null ? request.description().trim() : null,
                icon,
                order
        );

        KbCategory saved = categoryRepository.save(category);

        auditService.recordUserAction(
                user,
                AuditAction.KB_CATEGORY_CREATED,
                AuditTargetType.KB_CATEGORY,
                saved.getId(),
                saved.getSlug(),
                Map.of("name", saved.getName())
        );

        return KbCategoryResponse.from(saved, 0);
    }

    @Transactional
    public KbCategoryResponse updateCategory(UUID id, UpdateKbCategoryRequest request, User user) {
        KbCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));

        if (!category.getName().equalsIgnoreCase(request.name().trim())) {
            String newSlug = slugService.generateUniqueSlug(
                    request.name().trim(),
                    s -> categoryRepository.existsBySlugAndIdNot(s, id)
            );
            category.setSlug(newSlug);
        }

        category.setName(request.name().trim());
        if (request.description() != null) {
            category.setDescription(request.description().trim());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon().trim());
        }
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }

        KbCategory saved = categoryRepository.save(category);

        auditService.recordUserAction(
                user,
                AuditAction.KB_CATEGORY_UPDATED,
                AuditTargetType.KB_CATEGORY,
                saved.getId(),
                saved.getSlug(),
                Map.of("name", saved.getName())
        );

        long count = articleRepository.countByCategoryId(saved.getId());
        return KbCategoryResponse.from(saved, count);
    }
}
