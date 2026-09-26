package com.flowdesk.approval.controller;

import com.flowdesk.approval.dto.ApprovalResponse;
import com.flowdesk.approval.dto.ApproveApprovalRequest;
import com.flowdesk.approval.dto.RejectApprovalRequest;

import com.flowdesk.approval.service.ApprovalDecisionService;

import com.flowdesk.common.dto.PageResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    @Mock
    private ApprovalDecisionService
            approvalDecisionService;

    private ApprovalController controller;

    private UUID userId;

    private Jwt jwt;

    @BeforeEach
    void setUp() {
        controller =
                new ApprovalController(
                        approvalDecisionService
                );

        userId =
                UUID.randomUUID();

        jwt =
                mock(Jwt.class);

        when(jwt.getSubject())
                .thenReturn(
                        userId.toString()
                );
    }

    @Test
    void getPendingApprovalsDelegatesToService() {
        PageResponse<ApprovalResponse> page =
                new PageResponse<>(
                        List.of(),
                        0,
                        10,
                        0,
                        0,
                        true,
                        true
                );

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

        ResponseEntity<
                PageResponse<ApprovalResponse>>
                response =
                controller.getPendingApprovals(
                        jwt,
                        0,
                        10
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertSame(
                page,
                response.getBody()
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
    void approveDelegatesAuthenticatedUserAndRequest() {
        UUID approvalId =
                UUID.randomUUID();

        ApprovalResponse approvalResponse =
                mock(
                        ApprovalResponse.class
                );

        when(
                approvalDecisionService.approve(
                        userId,
                        approvalId,
                        "Approved"
                )
        ).thenReturn(
                approvalResponse
        );

        ResponseEntity<ApprovalResponse>
                response =
                controller.approve(
                        jwt,
                        approvalId,
                        new ApproveApprovalRequest(
                                "Approved"
                        )
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertSame(
                approvalResponse,
                response.getBody()
        );

        verify(
                approvalDecisionService
        ).approve(
                userId,
                approvalId,
                "Approved"
        );
    }

    @Test
    void rejectDelegatesAuthenticatedUserAndReason() {
        UUID approvalId =
                UUID.randomUUID();

        ApprovalResponse approvalResponse =
                mock(
                        ApprovalResponse.class
                );

        when(
                approvalDecisionService.reject(
                        userId,
                        approvalId,
                        "Budget not approved"
                )
        ).thenReturn(
                approvalResponse
        );

        ResponseEntity<ApprovalResponse>
                response =
                controller.reject(
                        jwt,
                        approvalId,
                        new RejectApprovalRequest(
                                "Budget not approved"
                        )
                );

        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );

        assertSame(
                approvalResponse,
                response.getBody()
        );

        verify(
                approvalDecisionService
        ).reject(
                userId,
                approvalId,
                "Budget not approved"
        );
    }
}