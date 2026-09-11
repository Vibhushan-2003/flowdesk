package com.flowdesk.auth.service;

import com.flowdesk.auth.dto.LoginRequest;
import com.flowdesk.auth.dto.LoginResponse;
import com.flowdesk.auth.security.AuthenticatedUser;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthService(
            AuthenticationManager authenticationManager,
            TokenService tokenService
    ) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    public LoginResponse login(LoginRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                normalizedEmail,
                                request.password()
                        )
                );

        AuthenticatedUser user =
                (AuthenticatedUser) authentication.getPrincipal();

        String accessToken =
                tokenService.generateAccessToken(user);

        return new LoginResponse(
                accessToken,
                "Bearer",
                user.id(),
                user.email(),
                user.roles()
        );
    }
}