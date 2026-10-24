package com.flowdesk.audit.controller;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.dto.AuditEventResponse;
import com.flowdesk.audit.service.AuditQueryService;

import com.flowdesk.common.dto.PageResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService
            auditQueryService;

    public AuditController(
            AuditQueryService auditQueryService
    ) {
        this.auditQueryService =
                auditQueryService;
    }

    @GetMapping("/events")
    public PageResponse<AuditEventResponse>
            getAuditEvents(

                    @RequestParam(
                            defaultValue = "0"
                    )
                    int page,

                    @RequestParam(
                            defaultValue = "50"
                    )
                    int size,

                    @RequestParam(
                            required = false
                    )
                    AuditAction action,

                    @RequestParam(
                            required = false
                    )
                    String search

            ) {

        return auditQueryService
                .getAuditEvents(
                        page,
                        size,
                        action,
                        search
                );
    }
}