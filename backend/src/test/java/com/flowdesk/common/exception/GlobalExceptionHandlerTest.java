
package com.flowdesk.common.exception;

import com.flowdesk.common.logging.RequestTracingFilter;
import com.flowdesk.user.exception.DuplicateEmailException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.slf4j.MDC;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private MockHttpServletRequest request() {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setRequestURI("/api/tickets/test");
        return request;
    }

    @AfterEach
    void cleanup() {
        MDC.remove(RequestTracingFilter.MDC_KEY);
    }

    @Test
    void includesRequestIdInErrorResponse() {

        MDC.put(
                RequestTracingFilter.MDC_KEY,
                "test-request-123"
        );

        ResponseEntity<ApiError> response =
                handler.handleResponseStatus(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Ticket not found"
                        ),
                        request()
                );

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(
                "test-request-123",
                response.getBody().requestId()
        );

        assertEquals(
                "/api/tickets/test",
                response.getBody().path()
        );
    }

    @Test
    void preservesBusinessErrorStatusAndMessage() {

        ResponseEntity<ApiError> response =
                handler.handleResponseStatus(
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Ticket already resolved"
                        ),
                        request()
                );

        assertEquals(409, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(
                "Ticket already resolved",
                response.getBody().message()
        );
    }

    @Test
    void hidesSensitiveServerErrorDetails() {

        ResponseEntity<ApiError> response =
                handler.handleResponseStatus(
                        new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Internal database connection details"
                        ),
                        request()
                );

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(
                "Service temporarily unavailable",
                response.getBody().message()
        );

        assertFalse(
                response.getBody().message()
                        .contains("database")
        );
    }

    @Test
    void unexpectedExceptionReturnsSafeMessage() {

        ResponseEntity<ApiError> response =
                handler.handleUnexpected(
                        new IllegalStateException(
                                "Internal secret details"
                        ),
                        request()
                );

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(
                "An unexpected error occurred",
                response.getBody().message()
        );

        assertFalse(
                response.getBody().message()
                        .contains("secret")
        );
    }

   
@Test
void duplicateEmailPreservesConflictStatus() {

    ResponseEntity<ApiError> response =
            handler.handleDuplicateEmail(
                    new DuplicateEmailException(
                            "Email already registered"
                    ),
                    request()
            );

    assertEquals(
            409,
            response.getStatusCode().value()
    );

    assertNotNull(response.getBody());

    assertEquals(
            "An account already exists for email: Email already registered",
            response.getBody().message()
    );
}

    @Test
    void forbiddenErrorDoesNotExposeOriginalReason() {

        ResponseEntity<ApiError> response =
                handler.handleResponseStatus(
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "Private resource details"
                        ),
                        request()
                );

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(
                "Access denied",
                response.getBody().message()
        );
    }
}
