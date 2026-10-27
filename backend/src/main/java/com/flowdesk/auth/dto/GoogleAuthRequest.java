package com.flowdesk.auth.dto;

public record GoogleAuthRequest(
        String code,
        String idToken,
        String state,
        String codeVerifier,
        String invitationToken,
        String linkToken,
        String flowDeskPassword
) {
    public GoogleAuthRequest(String code, String idToken, String state, String invitationToken, String flowDeskPassword) {
        this(code, idToken, state, null, invitationToken, null, flowDeskPassword);
    }
}
