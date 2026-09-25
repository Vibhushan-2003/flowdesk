package com.flowdesk.audit.controller;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.HttpHeaders;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${app.security.jwt.issuer}")
    private String issuer;

    @Test
    void adminCanReadAuditEvents() throws Exception {

        String token =
                createToken(
                        "admin@example.com",
                        "ADMIN"
                );

        mockMvc.perform(
                        get(
                                "/api/audit/events"
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    @Test
    void teamLeadCannotReadAuditEvents() throws Exception {

        String token =
                createToken(
                        "teamlead@example.com",
                        "TEAM_LEAD"
                );

        mockMvc.perform(
                        get(
                                "/api/audit/events"
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void supportEngineerCannotReadAuditEvents() throws Exception {

        String token =
                createToken(
                        "engineer@example.com",
                        "SUPPORT_ENGINEER"
                );

        mockMvc.perform(
                        get(
                                "/api/audit/events"
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void employeeCannotReadAuditEvents() throws Exception {

        String token =
                createToken(
                        "employee@example.com",
                        "EMPLOYEE"
                );

        mockMvc.perform(
                        get(
                                "/api/audit/events"
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void unauthenticatedRequestCannotReadAuditEvents()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/audit/events"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    private String createToken(
            String subject,
            String role
    ) {

        Instant now =
                Instant.now();

        JwtClaimsSet claims =
                JwtClaimsSet
                        .builder()
                        .issuer(
                                issuer
                        )
                        .subject(
                                subject
                        )
                        .issuedAt(
                                now
                        )
                        .expiresAt(
                                now.plus(
                                        30,
                                        ChronoUnit.MINUTES
                                )
                        )
                        .claim(
                                "roles",
                                List.of(
                                        role
                                )
                        )
                        .build();

        return jwtEncoder
                .encode(
                        JwtEncoderParameters.from(
                                claims
                        )
                )
                .getTokenValue();
    }
}