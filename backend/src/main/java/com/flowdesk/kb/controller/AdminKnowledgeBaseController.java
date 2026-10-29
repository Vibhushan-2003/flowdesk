package com.flowdesk.kb.controller;

import com.flowdesk.kb.dto.CreateKbCategoryRequest;
import com.flowdesk.kb.dto.KbCategoryResponse;
import com.flowdesk.kb.dto.UpdateKbCategoryRequest;
import com.flowdesk.kb.service.KnowledgeBaseService;
import com.flowdesk.user.domain.User;
import com.flowdesk.user.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/kb/categories")
public class AdminKnowledgeBaseController {

    private final KnowledgeBaseService kbService;
    private final UserRepository userRepository;

    public AdminKnowledgeBaseController(KnowledgeBaseService kbService, UserRepository userRepository) {
        this.kbService = kbService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    @GetMapping
    public ResponseEntity<List<KbCategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(kbService.getAllCategoriesForAdmin());
    }

    @PostMapping
    public ResponseEntity<KbCategoryResponse> createCategory(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateKbCategoryRequest request
    ) {
        User user = getAuthenticatedUser(jwt);
        KbCategoryResponse response = kbService.createCategory(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<KbCategoryResponse> updateCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateKbCategoryRequest request
    ) {
        User user = getAuthenticatedUser(jwt);
        KbCategoryResponse response = kbService.updateCategory(id, request, user);
        return ResponseEntity.ok(response);
    }
}
