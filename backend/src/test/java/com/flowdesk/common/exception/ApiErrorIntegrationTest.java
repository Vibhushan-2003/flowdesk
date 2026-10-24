
package com.flowdesk.common.exception;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void invalidPaginationReturnsMatchingRequestId()
            throws Exception {

        var result = mockMvc.perform(
                get("/api/tickets/my")
                        .param("page", "-1")
                        .accept(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message")
                .value("Page must be zero or greater"))
        .andExpect(jsonPath("$.requestId").isString())
        .andReturn();

        // Read the request ID from the HTTP response header.
        String headerId = result.getResponse()
                .getHeader("X-Request-ID");

        assertNotNull(headerId);

        // Check whether the header contains a valid UUID.
        assertDoesNotThrow(
                () -> UUID.fromString(headerId)
        );

        // Read the request ID from the JSON response body
        // of the SAME HTTP request.
        String jsonId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.requestId"
        );

        // Both IDs must be exactly the same.
        assertEquals(headerId, jsonId);
    }

    @Test
    void unauthorizedRequestStillReturns401()
            throws Exception {

        var result = mockMvc.perform(
                get("/api/tickets/my")
        )
        .andExpect(status().isUnauthorized())
        .andReturn();

        // Unauthorized requests must still have
        // a request ID in the response header.
        String headerId = result.getResponse()
                .getHeader("X-Request-ID");

        assertNotNull(headerId);

        // The request ID must be a valid UUID.
        assertDoesNotThrow(
                () -> UUID.fromString(headerId)
        );
    }
}
