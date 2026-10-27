
package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.AcceptInvitationRequest;
import com.flowdesk.auth.dto.AcceptInvitationResponse;
import com.flowdesk.auth.dto.ChangePasswordRequest;
import com.flowdesk.auth.dto.ChangePasswordResponse;
import com.flowdesk.auth.dto.ForgotPasswordRequest;
import com.flowdesk.auth.dto.ForgotPasswordResponse;
import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.dto.MeResponse;
import com.flowdesk.auth.dto.ResetPasswordRequest;
import com.flowdesk.auth.dto.ResetPasswordResponse;
import com.flowdesk.auth.dto.GoogleAuthRequest;
import com.flowdesk.auth.dto.GoogleOAuthConfigResponse;
import com.flowdesk.auth.service.AuthService;
import com.flowdesk.auth.service.GoogleAuthService;
import com.flowdesk.auth.service.PasswordResetService;
import com.flowdesk.user.service.UserInvitationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final UserInvitationService userInvitationService;
    private final GoogleAuthService googleAuthService;

    public AuthController(
            AuthService authService,
            PasswordResetService passwordResetService,
            UserInvitationService userInvitationService,
            GoogleAuthService googleAuthService
    ) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
        this.userInvitationService = userInvitationService;
        this.googleAuthService = googleAuthService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @GetMapping("/oauth2/config")
    public ResponseEntity<GoogleOAuthConfigResponse> getOAuthConfig() {
        return ResponseEntity.ok(
                googleAuthService.getOAuthConfig()
        );
    }

    @PostMapping("/oauth2/google")
    public ResponseEntity<LoginResponse> googleLogin(
            @RequestBody GoogleAuthRequest request
    ) {
        return ResponseEntity.ok(
                googleAuthService.authenticate(request)
        );
    }

    @PostMapping("/oauth2/google/link")
    public ResponseEntity<LoginResponse> completeGoogleLink(
            @Valid @RequestBody com.flowdesk.auth.dto.GoogleLinkAccountRequest request
    ) {
        return ResponseEntity.ok(
                googleAuthService.completeLinking(request.linkToken(), request.flowDeskPassword())
        );
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return ResponseEntity.ok(
                passwordResetService.requestPasswordReset(request)
        );
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return ResponseEntity.ok(
                passwordResetService.resetPassword(request)
        );
    }

    @PostMapping("/accept-invitation")
    public ResponseEntity<AcceptInvitationResponse> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request
    ) {
        return ResponseEntity.ok(
                userInvitationService.acceptInvitation(request)
        );
    }


    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {

        UUID userId =
                UUID.fromString(jwt.getSubject());

        String email =
                jwt.getClaimAsString("email");

        List<String> roles =
                authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(authority ->
                                authority.startsWith("ROLE_")
                        )
                        .map(authority ->
                                authority.substring(5)
                        )
                        .sorted()
                        .toList();

        return ResponseEntity.ok(
                new MeResponse(
                        userId,
                        email,
                        roles
                )
        );
    }

    @PostMapping("/change-password")
    public ResponseEntity<ChangePasswordResponse> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        return ResponseEntity.ok(
                authService.changePassword(userId, request)
        );
    }
}
