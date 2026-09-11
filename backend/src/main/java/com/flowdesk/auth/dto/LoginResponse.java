package com.flowdesk.auth.dto;

import com.flowdesk.user.domain.RoleCode;

import java.util.Set;
import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String tokenType,
        UUID userId,
        String email,
        Set<RoleCode> roles
) {
}