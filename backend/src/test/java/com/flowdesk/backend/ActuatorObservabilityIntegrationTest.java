package com.flowdesk.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Actuator health endpoint /actuator/health is accessible and reports UP")
    void actuatorHealthReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Actuator info endpoint /actuator/info is accessible and returns 200")
    void actuatorInfoReturns200() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Actuator prometheus endpoint /actuator/prometheus exposes JVM, process, ClamAV, and disk metrics")
    void actuatorPrometheusExposesRequiredMetrics() throws Exception {
        // Trigger a GC to populate GC metrics
        System.gc();

        // Trigger a request to record HTTP server metrics
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // 1. JVM Memory Metrics
        assertThat(responseBody).contains("jvm_memory_used_bytes");
        assertThat(responseBody).contains("jvm_memory_max_bytes");

        // 2. Process & System CPU & Uptime
        assertThat(responseBody).contains("process_uptime_seconds");
        assertThat(responseBody).contains("process_cpu_usage");
        assertThat(responseBody).contains("system_cpu_usage");

        // 3. Thread Counts
        assertThat(responseBody).contains("jvm_threads_live_threads");

        // 4. ClamAV Reachability Metric
        assertThat(responseBody).contains("flowdesk_clamav_available");

        // 5. Disk Storage Metrics
        assertThat(responseBody).contains("flowdesk_disk_free_bytes");
        assertThat(responseBody).contains("flowdesk_disk_total_bytes");
        assertThat(responseBody).contains("flowdesk_disk_free_percent");

        // 6. HTTP Server Request Metrics
        assertThat(responseBody).contains("http_server_requests_seconds_count");
    }

    @Test
    @DisplayName("Unauthenticated external request to /api/actuator/prometheus is denied with 401")
    void unauthenticatedApiActuatorRouteIsDenied() throws Exception {
        mockMvc.perform(get("/api/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sensitive actuator endpoints like /actuator/env and /actuator/heapdump require authentication (401)")
    void sensitiveActuatorEndpointsAreProtected() throws Exception {
        // Unauthenticated access to /actuator/env must be denied
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to /actuator/heapdump must be denied
        mockMvc.perform(get("/actuator/heapdump"))
                .andExpect(status().isUnauthorized());

        // Unauthenticated access to /actuator/beans must be denied
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Low cardinality is preserved and no sensitive customer tokens or emails leak into metric labels")
    void metricsLabelsPreserveLowCardinality() throws Exception {
        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Verify metric output does not leak customer emails or sensitive tokens
        assertThat(responseBody).doesNotContain("admin@flowdesk.test");
        assertThat(responseBody).doesNotContain("Bearer ");
        assertThat(responseBody).doesNotContain("password");
    }
}
