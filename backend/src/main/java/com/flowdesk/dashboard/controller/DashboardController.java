package com.flowdesk.dashboard.controller;

import com.flowdesk.dashboard.dto.DashboardMetricsResponse;
import com.flowdesk.dashboard.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardMetricsResponse> getDashboardStats(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID authenticatedUserId = UUID.fromString(jwt.getSubject());
        DashboardMetricsResponse response = dashboardService.getMetrics(authenticatedUserId);
        return ResponseEntity.ok(response);
    }
}

