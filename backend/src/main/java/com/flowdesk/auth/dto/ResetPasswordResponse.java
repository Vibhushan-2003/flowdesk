package com.flowdesk.auth.dto;

public record ResetPasswordResponse(
        String message
) {
    public static final String DEFAULT_SUCCESS_MESSAGE =
            "Password has been reset successfully. Please sign in with your new password.";

    public static ResetPasswordResponse defaultSuccess() {
        return new ResetPasswordResponse(DEFAULT_SUCCESS_MESSAGE);
    }
}
