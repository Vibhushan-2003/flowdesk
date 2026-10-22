package com.flowdesk.ticket.controller;

import com.flowdesk.ticket.service.SupportTicketService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SupportTicketResolutionEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportTicketService
            supportTicketService;

    @Test
    void unauthenticatedUserCannotResolveTicket()
            throws Exception {

        mockMvc.perform(
                patch(
                        "/api/support/tickets/FD-000070/resolve"
                )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "resolutionSummary":
                                    "Restarted the service."
                                }
                                """
                        )
        ).andExpect(
                status().isUnauthorized()
        );

        verifyNoInteractions(
                supportTicketService
        );
    }

    @Test
    void employeeCannotResolveTicket()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                patch(
                        "/api/support/tickets/FD-000070/resolve"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_EMPLOYEE"
                                                )
                                        )
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "resolutionSummary":
                                    "Restarted the service."
                                }
                                """
                        )
        ).andExpect(
                status().isForbidden()
        );

        verifyNoInteractions(
                supportTicketService
        );
    }

    @Test
    void supportEngineerCanResolveTicket()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                patch(
                        "/api/support/tickets/FD-000070/resolve"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_SUPPORT_ENGINEER"
                                                )
                                        )
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "resolutionSummary":
                                    "Restarted the service and confirmed normal operation."
                                }
                                """
                        )
        ).andExpect(
                status().isOk()
        );

        verify(
                supportTicketService
        ).resolveTicket(
                eq(userId),
                eq("FD-000070"),
                any()
        );
    }

    @Test
    void blankResolutionSummaryIsRejectedBeforeService()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                patch(
                        "/api/support/tickets/FD-000070/resolve"
                )
                        .with(
                                jwt()
                                        .jwt(jwt ->
                                                jwt.subject(
                                                        userId.toString()
                                                )
                                        )
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_SUPPORT_ENGINEER"
                                                )
                                        )
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "resolutionSummary": "   "
                                }
                                """
                        )
        ).andExpect(
                status().isBadRequest()
        );

        verifyNoInteractions(
                supportTicketService
        );
    }
}