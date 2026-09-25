package com.flowdesk.audit.service;

import com.flowdesk.audit.domain.AuditLog;
import com.flowdesk.audit.dto.AuditEventResponse;
import com.flowdesk.audit.repository.AuditLogRepository;

import com.flowdesk.common.dto.PageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AuditQueryService {

    private static final int MAX_PAGE_SIZE =
            100;

    private final AuditLogRepository
            auditLogRepository;

    public AuditQueryService(
            AuditLogRepository auditLogRepository
    ) {
        this.auditLogRepository =
                auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse>
            getAuditEvents(
                    int page,
                    int size
            ) {

        validatePagination(
                page,
                size
        );

        Page<AuditLog> auditPage =
                auditLogRepository
                        .findAllByOrderByOccurredAtDesc(
                                PageRequest.of(
                                        page,
                                        size
                                )
                        );

        return new PageResponse<>(
                auditPage
                        .getContent()
                        .stream()
                        .map(
                                this::toResponse
                        )
                        .toList(),
                auditPage.getNumber(),
                auditPage.getSize(),
                auditPage.getTotalElements(),
                auditPage.getTotalPages(),
                auditPage.isFirst(),
                auditPage.isLast()
        );
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be zero or greater"
            );
        }

        if (size < 1
                || size > MAX_PAGE_SIZE) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }
    }

    private AuditEventResponse toResponse(
            AuditLog auditLog
    ) {

        UUID actorUserId =
                auditLog.getActorUser() == null
                        ? null
                        : auditLog
                                .getActorUser()
                                .getId();

        return new AuditEventResponse(
                auditLog.getId(),
                auditLog.getActorType(),
                actorUserId,
                auditLog.getActorEmail(),
                auditLog.getAction(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getTargetReference(),
                auditLog.getMetadata(),
                auditLog.getOccurredAt()
        );
    }
}