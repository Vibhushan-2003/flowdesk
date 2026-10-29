package com.flowdesk.kb.controller;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.kb.domain.ArticleStatus;
import com.flowdesk.kb.dto.CreateKbArticleRequest;
import com.flowdesk.kb.dto.KbArticleResponse;
import com.flowdesk.kb.dto.KbArticleSummaryResponse;
import com.flowdesk.kb.dto.UpdateKbArticleRequest;
import com.flowdesk.kb.dto.UpdateKbArticleStatusRequest;
import com.flowdesk.kb.service.KnowledgeBaseService;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/support/kb/articles")
public class SupportKnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final UserRepository userRepository;

    public SupportKnowledgeBaseController(KnowledgeBaseService kbService, UserRepository userRepository) {
        this.kbService = kbService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    @GetMapping
    public ResponseEntity<PageResponse<KbArticleSummaryResponse>> searchArticles(
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(kbService.searchManagementArticles(status, categoryId, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KbArticleResponse> getArticleById(@PathVariable UUID id) {
        return ResponseEntity.ok(kbService.getArticleByIdForManagement(id));
    }

    @PostMapping
    public ResponseEntity<KbArticleResponse> createDraftArticle(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateKbArticleRequest request
    ) {
        User author = getAuthenticatedUser(jwt);
        KbArticleResponse response = kbService.createDraftArticle(request, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<KbArticleResponse> updateArticle(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateKbArticleRequest request
    ) {
        User user = getAuthenticatedUser(jwt);
        KbArticleResponse response = kbService.updateArticle(id, request, user);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<KbArticleResponse> transitionStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateKbArticleStatusRequest request
    ) {
        User user = getAuthenticatedUser(jwt);
        KbArticleResponse response = kbService.transitionStatus(id, request.status(), user);
        return ResponseEntity.ok(response);
    }
}
