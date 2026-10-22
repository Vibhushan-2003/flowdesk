
package com.flowdesk.ticket.controller;

import com.flowdesk.ticket.service.TicketService;
import com.flowdesk.ticket.service.SupportTicketService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketListAccessSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private SupportTicketService supportTicketService;

    @Test
    void guestCannotViewRequesterTickets() throws Exception {
        mockMvc.perform(get("/api/tickets/my"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ticketService);
    }

    @Test
    void guestCannotViewSupportAssignments() throws Exception {
        mockMvc.perform(get("/api/support/tickets/my"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(supportTicketService);
    }

    @Test
    void employeeCannotViewSupportAssignments() throws Exception {
        UUID employeeId = UUID.randomUUID();

        mockMvc.perform(
                get("/api/support/tickets/my")
                        .with(
                                jwt()
                                        .jwt(token -> token.subject(
                                                employeeId.toString()
                                        ))
                                        .authorities(
                                                new SimpleGrantedAuthority(
                                                        "ROLE_EMPLOYEE"
                                                )
                                        )
                        )
        ).andExpect(status().isForbidden());

        verifyNoInteractions(supportTicketService);
    }

    @Test
    void requesterListUsesEachAuthenticatedUserId() throws Exception {
        UUID requesterA = UUID.randomUUID();
        UUID requesterB = UUID.randomUUID();

        for (UUID requesterId : new UUID[]{requesterA, requesterB}) {
            mockMvc.perform(
                    get("/api/tickets/my")
                            .param("search", "vpn")
                            .param("sort", "newest")
                            .with(
                                    jwt()
                                            .jwt(token -> token.subject(
                                                    requesterId.toString()
                                            ))
                                            .authorities(
                                                    new SimpleGrantedAuthority(
                                                            "ROLE_EMPLOYEE"
                                                    )
                                            )
                            )
            ).andExpect(status().isOk());
        }

        verify(ticketService, times(1)).getMyTickets(
                eq(requesterA),
                eq(0),
                eq(10),
                eq("vpn"),
                isNull(),
                isNull(),
                eq("newest")
        );

        verify(ticketService, times(1)).getMyTickets(
                eq(requesterB),
                eq(0),
                eq(10),
                eq("vpn"),
                isNull(),
                isNull(),
                eq("newest")
        );
    }

    @Test
    void supportListUsesEachAuthenticatedEngineerId() throws Exception {
        UUID engineerA = UUID.randomUUID();
        UUID engineerB = UUID.randomUUID();

        for (UUID engineerId : new UUID[]{engineerA, engineerB}) {
            mockMvc.perform(
                    get("/api/support/tickets/my")
                            .param("search", "printer")
                            .param("sort", "oldest")
                            .with(
                                    jwt()
                                            .jwt(token -> token.subject(
                                                    engineerId.toString()
                                            ))
                                            .authorities(
                                                    new SimpleGrantedAuthority(
                                                            "ROLE_SUPPORT_ENGINEER"
                                                    )
                                            )
                            )
            ).andExpect(status().isOk());
        }

        verify(supportTicketService, times(1))
                .getMyAssignedTickets(
                        eq(engineerA),
                        eq(0),
                        eq(10),
                        eq("printer"),
                        isNull(),
                        isNull(),
                        eq("oldest")
                );

        verify(supportTicketService, times(1))
                .getMyAssignedTickets(
                        eq(engineerB),
                        eq(0),
                        eq(10),
                        eq("printer"),
                        isNull(),
                        isNull(),
                        eq("oldest")
                );
    }
}
