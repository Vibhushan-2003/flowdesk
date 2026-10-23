
package com.flowdesk.common.logging;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RequestTracingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpointHasRequestId() throws Exception {

        MvcResult result = mockMvc.perform(
                get("/api/health")
        ).andExpect(status().isOk()).andReturn();

        String id = result.getResponse()
                .getHeader("X-Request-ID");

        assertNotNull(id);
        assertEquals(UUID.fromString(id).toString(), id);
    }

    @Test
    void unauthorizedRequestHasRequestId() throws Exception {

        MvcResult result = mockMvc.perform(
                get("/api/tickets/my")
        ).andExpect(status().isUnauthorized()).andReturn();

        String id = result.getResponse()
                .getHeader("X-Request-ID");

        assertNotNull(id);
        assertDoesNotThrow(() -> UUID.fromString(id));
    }

    @Test
    void separateHttpRequestsHaveDifferentIds() throws Exception {

        MvcResult first = mockMvc.perform(
                get("/api/health")
        ).andExpect(status().isOk()).andReturn();

        MvcResult second = mockMvc.perform(
                get("/api/health")
        ).andExpect(status().isOk()).andReturn();

        assertNotEquals(
                first.getResponse().getHeader("X-Request-ID"),
                second.getResponse().getHeader("X-Request-ID")
        );
    }

    @Test
    void corsExposesRequestIdHeader() throws Exception {

        MvcResult result = mockMvc.perform(
                options("/api/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
        ).andExpect(status().isOk()).andReturn();

        String exposedHeaders = result.getResponse()
                .getHeader("Access-Control-Expose-Headers");

        assertNotNull(exposedHeaders);
        assertTrue(exposedHeaders.contains("X-Request-ID"));
        assertNotNull(
                result.getResponse().getHeader("X-Request-ID")
        );
    }
}
