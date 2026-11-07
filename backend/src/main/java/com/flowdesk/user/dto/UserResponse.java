package com.flowdesk.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.flowdesk.user.domain.RoleCode;
import com.flowdesk.user.domain.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        UserStatus status,
        Set<RoleCode> roles,
        Instant createdAt,
        String invitationUrl
) {
    public UserResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            UserStatus status,
            Set<RoleCode> roles,
            Instant createdAt
    ) {
        this(id, email, firstName, lastName, status, roles, createdAt, null);
    }
}
