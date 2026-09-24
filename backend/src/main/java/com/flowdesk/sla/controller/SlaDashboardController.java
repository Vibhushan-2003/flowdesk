package com.flowdesk.sla.controller;

import com.flowdesk.sla.dto.SlaDashboardResponse;
import com.flowdesk.sla.service.SlaDashboardService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sla")
public class SlaDashboardController {

    private final SlaDashboardService slaDashboardService;

    public SlaDashboardController(
            SlaDashboardService slaDashboardService
    ) {
        this.slaDashboardService = slaDashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<SlaDashboardResponse> getDashboard() {
        return ResponseEntity.ok(
                slaDashboardService.getDashboard()
        );
    }
}
