package com.flowdesk.user.dto;

import com.flowdesk.user.domain.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserInvitationRequest(
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be valid")
        @Size(max = 255, message = "Email must be 255 characters or fewer")
        String email,

        @NotBlank(message = "First name must not be blank")
        @Size(max = 100, message = "First name must be 100 characters or fewer")
        String firstName,

        @NotBlank(message = "Last name must not be blank")
        @Size(max = 100, message = "Last name must be 100 characters or fewer")
        String lastName,

        @NotEmpty(message = "At least one role must be assigned")
        Set<RoleCode> roles
) {
}
