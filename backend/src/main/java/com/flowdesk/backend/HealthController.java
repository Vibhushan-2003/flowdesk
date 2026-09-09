package com.flowdesk.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    // TEMPORARY: Day 5 health check endpoint to verify backend starts
    @GetMapping("/api/health")
    public String health() {
        return "Backend is running!";
    }
}
