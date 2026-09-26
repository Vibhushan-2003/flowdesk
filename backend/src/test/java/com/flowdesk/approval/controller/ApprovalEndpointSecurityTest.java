package com.flowdesk.approval.controller;

import com.flowdesk.approval.dto.ApprovalResponse;
import com.flowdesk.approval.service.ApprovalDecisionService;

import com.flowdesk.common.dto.PageResponse;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.http.MediaType;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApprovalEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApprovalDecisionService
            approvalDecisionService;

    @Test
    void unauthenticatedUserCannotAccessApprovals()
            throws Exception {

        mockMvc.perform(
                get(
                        "/api/approvals/pending"
                )
        ).andExpect(
                status().isUnauthorized()
        );
    }

    @Test
    void employeeCannotAccessApprovals()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                get(
                        "/api/approvals/pending"
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
        ).andExpect(
                status().isForbidden()
        );
    }

    @Test
    void supportEngineerCannotAccessApprovals()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        mockMvc.perform(
                get(
                        "/api/approvals/pending"
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
        ).andExpect(
                status().isForbidden()
        );
    }

    @Test
    void teamLeadCanAccessApprovals()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        PageResponse<ApprovalResponse> page =
                emptyPage();

        when(
                approvalDecisionService
                        .getPendingApprovals(
                                userId,
                                0,
                                10
                        )
        ).thenReturn(
                page
        );

        mockMvc.perform(
                get(
                        "/api/approvals/pending"
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
                                                        "ROLE_TEAM_LEAD"
                                                )
                                        )
                        )
        )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath(
                                "$.content"
                        ).isArray()
                );

        verify(
                approvalDecisionService
        ).getPendingApprovals(
                userId,
                0,
                10
        );
    }

    @Test
    void adminCanAccessApprovals()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        PageResponse<ApprovalResponse> page =
                emptyPage();

        when(
                approvalDecisionService
                        .getPendingApprovals(
                                userId,
                                0,
                                10
                        )
        ).thenReturn(
                page
        );

        mockMvc.perform(
                get(
                        "/api/approvals/pending"
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
                                                        "ROLE_ADMIN"
                                                )
                                        )
                        )
        ).andExpect(
                status().isOk()
        );

        verify(
                approvalDecisionService
        ).getPendingApprovals(
                userId,
                0,
                10
        );
    }

    @Test
    void blankRejectionReasonReturnsBadRequest()
            throws Exception {

        UUID userId =
                UUID.randomUUID();

        UUID approvalId =
                UUID.randomUUID();

        mockMvc.perform(
                post(
                        "/api/approvals/{approvalId}/reject",
                        approvalId
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
                                                        "ROLE_TEAM_LEAD"
                                                )
                                        )
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .content(
                                """
                                {
                                  "reason": "   "
                                }
                                """
                        )
        ).andExpect(
                status().isBadRequest()
        );

        verify(
                approvalDecisionService,
                never()
        ).reject(
                eq(userId),
                eq(approvalId),
                eq("   ")
        );
    }

    private PageResponse<ApprovalResponse>
            emptyPage() {

        return new PageResponse<>(
                List.of(),
                0,
                10,
                0,
                0,
                true,
                true
        );
    }
}