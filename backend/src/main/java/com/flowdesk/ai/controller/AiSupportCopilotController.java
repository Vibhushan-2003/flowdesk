package com.flowdesk.ai.controller;

import com.flowdesk.ai.dto.AiSupportCopilotRequest;
import com.flowdesk.ai.dto.AiSupportCopilotResponse;

import com.flowdesk.ai.service.AiSupportCopilotService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/support/tickets/{ticketNumber}/ai-copilot"
)
public class AiSupportCopilotController {

    private final AiSupportCopilotService
            aiSupportCopilotService;

    public AiSupportCopilotController(
            AiSupportCopilotService aiSupportCopilotService
    ) {
        this.aiSupportCopilotService =
                aiSupportCopilotService;
    }

    @PostMapping
    public ResponseEntity<AiSupportCopilotResponse>
            ask(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable String ticketNumber,
                    @Valid
                    @RequestBody
                    AiSupportCopilotRequest request
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        AiSupportCopilotResponse response =
                aiSupportCopilotService
                        .ask(
                                authenticatedUserId,
                                ticketNumber,
                                request
                        );

        return ResponseEntity.ok(
                response
        );
    }
}