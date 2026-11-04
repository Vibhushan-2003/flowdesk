package com.flowdesk.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendInvitationResponse(
        String message,
        String invitationUrl
) {
    public ResendInvitationResponse(String message) {
        this(message, null);
    }
}
