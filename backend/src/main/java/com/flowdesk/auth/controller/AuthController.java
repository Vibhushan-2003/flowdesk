
package com.flowdesk.auth.controller;

import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.dto.MeResponse;
import com.flowdesk.auth.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

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

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
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
}
