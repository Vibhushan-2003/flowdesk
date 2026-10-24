package com.flowdesk.user.dto;

import com.flowdesk.user.domain.RoleCode;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record UpdateUserRolesRequest(

        @NotEmpty(message = "At least one role must be assigned")
        Set<RoleCode> roles
) {
}
