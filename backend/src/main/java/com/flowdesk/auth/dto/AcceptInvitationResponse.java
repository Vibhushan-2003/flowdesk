package com.flowdesk.auth.dto;

public record AcceptInvitationResponse(
        String message
) {
    public static AcceptInvitationResponse defaultSuccess() {
        return new AcceptInvitationResponse("Invitation accepted successfully. You can now log in.");
    }
}
