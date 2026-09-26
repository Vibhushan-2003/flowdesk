package com.flowdesk.approval.controller;

import com.flowdesk.approval.dto.ApprovalResponse;
import com.flowdesk.approval.dto.ApproveApprovalRequest;
import com.flowdesk.approval.dto.RejectApprovalRequest;

import com.flowdesk.approval.service.ApprovalDecisionService;

import com.flowdesk.common.dto.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalDecisionService
            approvalDecisionService;

    public ApprovalController(
            ApprovalDecisionService approvalDecisionService
    ) {
        this.approvalDecisionService =
                approvalDecisionService;
    }

    @GetMapping("/pending")
    public ResponseEntity<
            PageResponse<ApprovalResponse>>
            getPendingApprovals(
                    @AuthenticationPrincipal Jwt jwt,
                    @RequestParam(
                            defaultValue = "0"
                    )
                    int page,
                    @RequestParam(
                            defaultValue = "10"
                    )
                    int size
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        PageResponse<ApprovalResponse> response =
                approvalDecisionService
                        .getPendingApprovals(
                                authenticatedUserId,
                                page,
                                size
                        );

        return ResponseEntity.ok(
                response
        );
    }

    @PostMapping("/{approvalId}/approve")
    public ResponseEntity<ApprovalResponse>
            approve(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable UUID approvalId,
                    @Valid
                    @RequestBody
                    ApproveApprovalRequest request
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        ApprovalResponse response =
                approvalDecisionService
                        .approve(
                                authenticatedUserId,
                                approvalId,
                                request.note()
                        );

        return ResponseEntity.ok(
                response
        );
    }

    @PostMapping("/{approvalId}/reject")
    public ResponseEntity<ApprovalResponse>
            reject(
                    @AuthenticationPrincipal Jwt jwt,
                    @PathVariable UUID approvalId,
                    @Valid
                    @RequestBody
                    RejectApprovalRequest request
            ) {

        UUID authenticatedUserId =
                UUID.fromString(
                        jwt.getSubject()
                );

        ApprovalResponse response =
                approvalDecisionService
                        .reject(
                                authenticatedUserId,
                                approvalId,
                                request.reason()
                        );

        return ResponseEntity.ok(
                response
        );
    }
}