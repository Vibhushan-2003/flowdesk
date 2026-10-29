package com.flowdesk.kb.controller;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.kb.dto.KbArticleDeflectionResponse;
import com.flowdesk.kb.dto.KbArticleResponse;
import com.flowdesk.kb.dto.KbArticleSummaryResponse;
import com.flowdesk.kb.dto.KbCategoryResponse;
import com.flowdesk.kb.service.KnowledgeBaseService;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final UserRepository userRepository;

    public KnowledgeBaseController(KnowledgeBaseService kbService, UserRepository userRepository) {
        this.kbService = kbService;
        this.userRepository = userRepository;
    }

    @GetMapping("/categories")
    public ResponseEntity<List<KbCategoryResponse>> getActiveCategories() {
        return ResponseEntity.ok(kbService.getActiveCategories());
    }

    @GetMapping("/articles")
    public ResponseEntity<PageResponse<KbArticleSummaryResponse>> searchArticles(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(kbService.searchPublishedArticles(categoryId, search, page, size));
    }

    @GetMapping("/articles/{slug}")
    public ResponseEntity<KbArticleResponse> getArticleBySlug(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String slug
    ) {
        User user = null;
        if (jwt != null) {
            UUID userId = UUID.fromString(jwt.getSubject());
            user = userRepository.findById(userId).orElse(null);
        }
        return ResponseEntity.ok(kbService.getArticleBySlug(slug, user));
    }

    @GetMapping("/articles/deflect")
    public ResponseEntity<List<KbArticleDeflectionResponse>> getDeflectionSuggestions(
            @RequestParam(required = false) String query
    ) {
        return ResponseEntity.ok(kbService.getDeflectionSuggestions(query));
    }
}
