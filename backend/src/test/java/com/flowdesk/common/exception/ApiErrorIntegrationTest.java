
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
void unauthorizedRequestReturnsStandardJson()
        throws Exception {

    var result = mockMvc.perform(
            get("/api/tickets/my")
                    .accept(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isUnauthorized())
    .andExpect(content().contentTypeCompatibleWith(
            MediaType.APPLICATION_JSON
    ))
    .andExpect(jsonPath("$.status").value(401))
    .andExpect(jsonPath("$.message")
            .value("Authentication required"))
    .andExpect(jsonPath("$.path")
            .value("/api/tickets/my"))
    .andExpect(jsonPath("$.requestId").isString())
    .andReturn();

    String headerId = result.getResponse()
            .getHeader("X-Request-ID");

    assertNotNull(headerId);
    assertDoesNotThrow(() -> UUID.fromString(headerId));

    String bodyId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.requestId"
    );

    assertEquals(headerId, bodyId);
}

@Test
@WithMockUser(roles = "EMPLOYEE")
void employeeCannotAccessAdminAuditEndpoint()
        throws Exception {

    var result = mockMvc.perform(
            get("/api/audit")
                    .accept(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isForbidden())
    .andExpect(content().contentTypeCompatibleWith(
            MediaType.APPLICATION_JSON
    ))
    .andExpect(jsonPath("$.status").value(403))
    .andExpect(jsonPath("$.message")
            .value("Access denied"))
    .andExpect(jsonPath("$.requestId").isString())
    .andReturn();

    String headerId = result.getResponse()
            .getHeader("X-Request-ID");

    assertNotNull(headerId);
    assertDoesNotThrow(() -> UUID.fromString(headerId));

    String bodyId = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.requestId"
    );

    assertEquals(headerId, bodyId);
}

@Test
void invalidBearerTokenReturns401()
        throws Exception {

    mockMvc.perform(
            get("/api/tickets/my")
                    .header(
                            "Authorization",
                            "Bearer invalid.jwt.token"
                    )
                    .accept(MediaType.APPLICATION_JSON)
    )
    .andExpect(status().isUnauthorized())
    .andExpect(header().string(
        "WWW-Authenticate",
        org.hamcrest.Matchers.startsWith("Bearer")
))
    .andExpect(content().contentTypeCompatibleWith(
            MediaType.APPLICATION_JSON
    ))
    .andExpect(jsonPath("$.status").value(401))
    .andExpect(jsonPath("$.message")
            .value("Authentication required"))
    .andExpect(jsonPath("$.requestId").isString());
}

}
