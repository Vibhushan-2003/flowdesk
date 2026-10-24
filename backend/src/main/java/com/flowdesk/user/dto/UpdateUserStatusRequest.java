package com.flowdesk.user.dto;

import com.flowdesk.user.domain.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(

        @NotNull(message = "Status is required")
        UserStatus status
) {
}
