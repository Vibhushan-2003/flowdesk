package com.flowdesk.auth.dto;

public record GoogleOAuthConfigResponse(
        boolean enabled,
        String clientId,
        String redirectUri,
        String authUri,
        boolean mailEnabled
) {
    public GoogleOAuthConfigResponse(boolean enabled, String clientId, String redirectUri, String authUri) {
        this(enabled, clientId, redirectUri, authUri, true);
    }
}
