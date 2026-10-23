
package com.flowdesk.common.logging;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.slf4j.MDC;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RequestTracingFilterTest {

    private final RequestTracingFilter filter =
            new RequestTracingFilter();

    @AfterEach
    void cleanUp() {
        MDC.remove(RequestTracingFilter.MDC_KEY);
    }

    @Test
    void generatesRequestIdAndExposesItInResponse()
            throws ServletException, IOException {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        AtomicReference<String> idInsideChain =
                new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            idInsideChain.set(
                    MDC.get(RequestTracingFilter.MDC_KEY)
            );
        });

        String responseId = response.getHeader(
                RequestTracingFilter.REQUEST_ID_HEADER
        );

        assertNotNull(responseId);
        assertEquals(
                UUID.fromString(responseId).toString(),
                responseId
        );
        assertEquals(responseId, idInsideChain.get());
        assertNull(MDC.get(RequestTracingFilter.MDC_KEY));
    }

    @Test
    void ignoresUntrustedIncomingRequestId()
            throws ServletException, IOException {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                RequestTracingFilter.REQUEST_ID_HEADER,
                "untrusted-value"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> {}
        );

        String responseId = response.getHeader(
                RequestTracingFilter.REQUEST_ID_HEADER
        );

        assertNotEquals("untrusted-value", responseId);
        assertDoesNotThrow(() -> UUID.fromString(responseId));
    }

    @Test
    void clearsMdcEvenWhenProcessingFails() {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        assertThrows(
                ServletException.class,
                () -> filter.doFilter(
                        request,
                        response,
                        (req, res) -> {
                            throw new ServletException("Test failure");
                        }
                )
        );

        assertNotNull(response.getHeader(
                RequestTracingFilter.REQUEST_ID_HEADER
        ));

        assertNull(MDC.get(RequestTracingFilter.MDC_KEY));
    }

    @Test
    void restoresExistingMdcValue()
            throws ServletException, IOException {

        MDC.put(RequestTracingFilter.MDC_KEY, "previous-id");

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (req, res) -> assertNotEquals(
                        "previous-id",
                        MDC.get(RequestTracingFilter.MDC_KEY)
                )
        );

        assertEquals(
                "previous-id",
                MDC.get(RequestTracingFilter.MDC_KEY)
        );
    }

    @Test
    void generatesDifferentIdsForDifferentRequests()
            throws ServletException, IOException {

        MockHttpServletResponse first =
                new MockHttpServletResponse();

        MockHttpServletResponse second =
                new MockHttpServletResponse();

        filter.doFilter(
                new MockHttpServletRequest(),
                first,
                (req, res) -> {}
        );

        filter.doFilter(
                new MockHttpServletRequest(),
                second,
                (req, res) -> {}
        );

        assertNotEquals(
                first.getHeader(RequestTracingFilter.REQUEST_ID_HEADER),
                second.getHeader(RequestTracingFilter.REQUEST_ID_HEADER)
        );
    }
}
