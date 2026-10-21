package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiReplyDraftResponse;
import com.flowdesk.ai.service.AiReplyDraftService;

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
        "/api/support/tickets/{ticketNumber}/ai-reply-draft"
)
public class AiReplyDraftController {

    private final AiReplyDraftService
            aiReplyDraftService;

    public AiReplyDraftController(
            AiReplyDraftService aiReplyDraftService
    ) {
        this.aiReplyDraftService =
                aiReplyDraftService;
    }

    @PostMapping
    public ResponseEntity<AiReplyDraftResponse>
            generateDraft(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        AiReplyDraftResponse response =
                aiReplyDraftService
                        .generateDraft(
                                authenticatedUserId,
                                ticketNumber
                        );

        return ResponseEntity.ok(
                response
        );
    }
}