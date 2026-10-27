package com.flowdesk.auth.dto;

import java.time.Instant;
import java.util.Map;

public record OAuthLinkingRequiredResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String linkToken,
        String email,
        String provider,
        Map<String, String> fieldErrors,
        String requestId
) {
}
