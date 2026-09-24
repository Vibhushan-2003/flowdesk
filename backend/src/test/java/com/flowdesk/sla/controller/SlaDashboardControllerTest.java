package com.flowdesk.sla.controller;

import com.flowdesk.sla.dto.SlaDashboardResponse;
import com.flowdesk.sla.service.SlaDashboardService;
import com.flowdesk.ticket.domain.TicketPriority;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlaDashboardControllerTest {

    @Mock
    private SlaDashboardService slaDashboardService;

    @Test
    void getDashboardReturnsServiceResponse() {
        SlaDashboardResponse dashboard =
                new SlaDashboardResponse(
                        4L,
                        1L,
                        1L,
                        90.0,
                        80.0,
                        Map.of(
                                TicketPriority.LOW, 0L,
                                TicketPriority.MEDIUM, 1L,
                                TicketPriority.HIGH, 0L,
                                TicketPriority.CRITICAL, 0L
                        ),
                        List.of()
                );

        when(
                slaDashboardService.getDashboard()
        ).thenReturn(dashboard);

        SlaDashboardController controller =
                new SlaDashboardController(
                        slaDashboardService
                );

        ResponseEntity<SlaDashboardResponse> response =
                controller.getDashboard();

        assertSame(
                dashboard,
                response.getBody()
        );

        verify(
                slaDashboardService
        ).getDashboard();
    }
}
