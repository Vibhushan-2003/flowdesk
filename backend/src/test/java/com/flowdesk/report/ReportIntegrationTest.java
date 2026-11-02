package com.flowdesk.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Report Integration Tests against Real Schema")
class ReportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Admin can fetch operational report without projection failure")
    void operationalReport_asAdmin_succeeds() throws Exception {
        UUID adminId = UUID.randomUUID();

        mockMvc.perform(get("/api/reports/operational")
                        .with(jwt().jwt(j -> j.subject(adminId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyTrends", notNullValue()))
                .andExpect(jsonPath("$.ticketVolume", notNullValue()))
                .andExpect(jsonPath("$.slaCompliance", notNullValue()));
    }

    @Test
    @DisplayName("Team lead can export operational report CSV")
    void operationalReportCsv_asTeamLead_succeeds() throws Exception {
        UUID leadId = UUID.randomUUID();

        mockMvc.perform(get("/api/reports/operational/csv")
                        .with(jwt().jwt(j -> j.subject(leadId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_TEAM_LEAD"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"));
    }

    @Test
    @DisplayName("Employee is forbidden from accessing operational report")
    void operationalReport_asEmployee_isForbidden() throws Exception {
        UUID employeeId = UUID.randomUUID();

        mockMvc.perform(get("/api/reports/operational")
                        .with(jwt().jwt(j -> j.subject(employeeId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }
}

