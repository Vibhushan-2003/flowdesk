package com.flowdesk.auth.dto;

public record ForgotPasswordResponse(
        String message
) {
    public static final String GENERIC_SUCCESS_MESSAGE =
            "If an active account exists with that email address, a password reset link has been sent.";

    public static ForgotPasswordResponse genericSuccess() {
        return new ForgotPasswordResponse(GENERIC_SUCCESS_MESSAGE);
    }
}
