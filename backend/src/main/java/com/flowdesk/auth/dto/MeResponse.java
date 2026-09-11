package com.flowdesk.auth.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
        UUID userId,
        String email,
        List<String> roles
) {
}