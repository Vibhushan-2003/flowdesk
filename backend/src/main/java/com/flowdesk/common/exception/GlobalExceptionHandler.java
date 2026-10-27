
package com.flowdesk.common.exception;

import com.flowdesk.auth.dto.OAuthLinkingRequiredResponse;
import com.flowdesk.auth.exception.OAuthLinkingRequiredException;
import com.flowdesk.common.logging.RequestTracingFilter;
import com.flowdesk.user.exception.DuplicateEmailException;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private String currentRequestId() {
        return MDC.get(RequestTracingFilter.MDC_KEY);
    }

    private ResponseEntity<ApiError> buildError(
            HttpStatusCode status,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        String errorName =
                status instanceof HttpStatus httpStatus
                        ? httpStatus.getReasonPhrase()
                        : "HTTP " + status.value();

        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                errorName,
                message,
                request.getRequestURI(),
                fieldErrors,
                currentRequestId()
        );

        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiError> handleDuplicateEmail(
            DuplicateEmailException exception,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.CONFLICT,
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(OAuthLinkingRequiredException.class)
    public ResponseEntity<OAuthLinkingRequiredResponse> handleOAuthLinkingRequired(
            OAuthLinkingRequiredException exception,
            HttpServletRequest request
    ) {
        OAuthLinkingRequiredResponse response = new OAuthLinkingRequiredResponse(
                Instant.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                exception.getMessage(),
                request.getRequestURI(),
                exception.getLinkToken(),
                exception.getEmail(),
                exception.getProvider(),
                Map.of("linkToken", exception.getLinkToken()),
                currentRequestId()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler({
            BadCredentialsException.class,
            UsernameNotFoundException.class,
            DisabledException.class
    })
    public ResponseEntity<ApiError> handleAuthenticationFailure(
            RuntimeException exception,
            HttpServletRequest request
    ) {
        return buildError(
                HttpStatus.UNAUTHORIZED,
                "Invalid email or password",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors =
                new LinkedHashMap<>();

        exception.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        fieldErrors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return buildError(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode status = exception.getStatusCode();

        String message;

        if (status.is5xxServerError()) {
            message = "Service temporarily unavailable";
        } else if (status.value() == 401) {
            message = "Authentication required";
        } else if (status.value() == 403) {
            message = "Access denied";
        } else {
            message = exception.getReason();

            if (message == null || message.isBlank()) {
                message = "Request could not be completed";
            }
        }

        return buildError(
                status,
                message,
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        log.error(
                "Unhandled API exception",
                exception
        );

        return buildError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request,
                Map.of()
        );
    }
}
