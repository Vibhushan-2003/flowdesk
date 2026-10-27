package com.flowdesk.user.controller;

import com.flowdesk.common.dto.PageResponse;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.UserStatus;
import com.flowdesk.user.dto.AdminCreateUserRequest;
import com.flowdesk.user.dto.UpdateUserRolesRequest;
import com.flowdesk.user.dto.UpdateUserStatusRequest;
import com.flowdesk.user.dto.UserResponse;
import com.flowdesk.user.dto.CreateUserInvitationRequest;
import com.flowdesk.user.dto.ResendInvitationResponse;
import com.flowdesk.user.service.AdminUserService;
import com.flowdesk.user.service.UserInvitationService;

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

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final UserInvitationService userInvitationService;

    public AdminUserController(
            AdminUserService adminUserService,
            UserInvitationService userInvitationService
    ) {
        this.adminUserService = adminUserService;
        this.userInvitationService = userInvitationService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleCode role,
            @RequestParam(required = false) UserStatus status
    ) {
        PageResponse<UserResponse> response = adminUserService.searchUsers(
                page,
                size,
                search,
                role,
                status
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(
            @PathVariable UUID userId
    ) {
        UserResponse response = adminUserService.getUserById(userId);

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        UUID actorUserId = UUID.fromString(jwt.getSubject());
        UserResponse response = adminUserService.createUser(request, actorUserId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PutMapping("/{userId}/roles")
    public ResponseEntity<UserResponse> updateUserRoles(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRolesRequest request
    ) {
        UUID actorUserId = UUID.fromString(jwt.getSubject());
        UserResponse response = adminUserService.updateUserRoles(
                userId,
                request,
                actorUserId
        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}/status")
    public ResponseEntity<UserResponse> updateUserStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        UUID actorUserId = UUID.fromString(jwt.getSubject());
        UserResponse response = adminUserService.updateUserStatus(
                userId,
                request,
                actorUserId
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/invitations")
    public ResponseEntity<UserResponse> createInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUserInvitationRequest request
    ) {
        UUID actorUserId = UUID.fromString(jwt.getSubject());
        UserResponse response = userInvitationService.createInvitation(request, actorUserId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{userId}/invitations/resend")
    public ResponseEntity<ResendInvitationResponse> resendInvitation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID userId
    ) {
        UUID actorUserId = UUID.fromString(jwt.getSubject());
        ResendInvitationResponse response = userInvitationService.resendInvitation(userId, actorUserId);

        return ResponseEntity.ok(response);
    }
}
