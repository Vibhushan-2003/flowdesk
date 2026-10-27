package com.flowdesk.auth.dto;

public record GoogleOAuthConfigResponse(
        boolean enabled,
        String clientId,
        String redirectUri,
        String authUri
) {
}
