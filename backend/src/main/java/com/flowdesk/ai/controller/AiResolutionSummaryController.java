package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiResolutionSummaryResponse;
import com.flowdesk.ai.service.AiResolutionSummaryService;

import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/support/tickets/{ticketNumber}/ai-resolution-summary"
)
public class AiResolutionSummaryController {

    private final AiResolutionSummaryService
            aiResolutionSummaryService;

    public AiResolutionSummaryController(
            AiResolutionSummaryService aiResolutionSummaryService
    ) {
        this.aiResolutionSummaryService =
                aiResolutionSummaryService;
    }

    @PostMapping
    public ResponseEntity<AiResolutionSummaryResponse>
            generateDraft(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        AiResolutionSummaryResponse response =
                aiResolutionSummaryService
                        .generateDraft(
                                authenticatedUserId,
                                ticketNumber
                        );

        return ResponseEntity.ok(
                response
        );
    }
}