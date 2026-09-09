package com.flowdesk.user.dto;

import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<RoleCode> roles,
        Instant createdAt
) {
}