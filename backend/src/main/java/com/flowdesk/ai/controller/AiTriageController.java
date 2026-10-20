package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiTriageApplicationResponse;
import com.flowdesk.ai.dto.AiTriageResponse;

import com.flowdesk.ai.service.AiTriageApplicationService;
import com.flowdesk.ai.service.AiTriageService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/support/tickets/{ticketNumber}/ai-triage"
)
public class AiTriageController {

    private final AiTriageService
            aiTriageService;

    private final AiTriageApplicationService
            aiTriageApplicationService;

    public AiTriageController(
            AiTriageService aiTriageService,
            AiTriageApplicationService aiTriageApplicationService
    ) {
        this.aiTriageService =
                aiTriageService;

        this.aiTriageApplicationService =
                aiTriageApplicationService;
    }

    @PostMapping
    public ResponseEntity<AiTriageResponse>
            generateRecommendation(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        AiTriageResponse response =
                aiTriageService
                        .generateRecommendation(
                                authenticatedUserId,
                                ticketNumber
                        );

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        response
                );
    }

    @GetMapping
    public ResponseEntity<AiTriageResponse>
            getRecommendation(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        AiTriageResponse response =
                aiTriageService
                        .getRecommendation(
                                authenticatedUserId,
                                ticketNumber
                        );

        return ResponseEntity.ok(
                response
        );
    }

    @PostMapping("/apply")
    public ResponseEntity<AiTriageApplicationResponse>
            applyRecommendation(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        AiTriageApplicationResponse response =
                aiTriageApplicationService
                        .applyRecommendation(
                                authenticatedUserId,
                                ticketNumber
                        );

        return ResponseEntity.ok(
                response
        );
    }
}