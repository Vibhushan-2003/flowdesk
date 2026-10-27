package com.flowdesk.auth.dto;

public record GoogleUserInfo(
        String sub,
        String email,
        String name,
        boolean emailVerified
) {
}
