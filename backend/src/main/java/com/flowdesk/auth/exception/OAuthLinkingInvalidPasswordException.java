package com.flowdesk.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown when password verification fails during Google account linking.
 * Extends ResponseStatusException so that Spring MVC returns HTTP 400 Bad Request,
 * while allowing GoogleAuthService to designate noRollbackFor so that PostgreSQL
 * commits the incremented failed attempt counter and permanent invalidation.
 */
public class OAuthLinkingInvalidPasswordException extends ResponseStatusException {

    private final int remainingAttempts;
    private final boolean permanentlyInvalidated;

    public OAuthLinkingInvalidPasswordException(int remainingAttempts, boolean permanentlyInvalidated) {
        super(
                HttpStatus.BAD_REQUEST,
                permanentlyInvalidated
                        ? "Too many failed password attempts. This linking transaction has been invalidated."
                        : "Invalid password for account linking. (" + remainingAttempts + " attempts remaining)"
        );
        this.remainingAttempts = remainingAttempts;
        this.permanentlyInvalidated = permanentlyInvalidated;
    }

    public int getRemainingAttempts() {
        return remainingAttempts;
    }

    public boolean isPermanentlyInvalidated() {
        return permanentlyInvalidated;
    }
}
