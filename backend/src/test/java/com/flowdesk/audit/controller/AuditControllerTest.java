package com.flowdesk.audit.controller;

import com.flowdesk.audit.dto.AuditEventResponse;
import com.flowdesk.audit.service.AuditQueryService;

import com.flowdesk.common.dto.PageResponse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

    @Mock
    private AuditQueryService
            auditQueryService;

    @InjectMocks
    private AuditController
            auditController;

    @Test
    void getAuditEventsDelegatesPaginationToService() {

        PageResponse<AuditEventResponse> expectedResponse =
                new PageResponse<>(
                        List.of(),
                        0,
                        50,
                        0,
                        0,
                        true,
                        true
                );

        when(
                auditQueryService
                        .getAuditEvents(
                                0,
                                50
                        )
        ).thenReturn(
                expectedResponse
        );

        PageResponse<AuditEventResponse> actualResponse =
                auditController
                        .getAuditEvents(
                                0,
                                50
                        );

        assertSame(
                expectedResponse,
                actualResponse
        );

        verify(
                auditQueryService
        ).getAuditEvents(
                0,
                50
        );
    }
}