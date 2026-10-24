package com.flowdesk.audit.service;

import com.flowdesk.audit.domain.AuditAction;
import com.flowdesk.audit.domain.AuditLog;
import com.flowdesk.audit.dto.AuditEventResponse;
import com.flowdesk.audit.repository.AuditLogRepository;

import com.flowdesk.common.dto.PageResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
        return getAuditEvents(page, size, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse>
            getAuditEvents(
                    int page,
                    int size,
                    AuditAction action,
                    String search
            ) {

        validatePagination(
                page,
                size
        );

        String searchPattern =
                buildSearchPattern(search);

        Pageable pageable =
                PageRequest.of(
                        page,
                        size
                );

        Page<AuditLog> auditPage;
        if (action == null && searchPattern == null) {
            auditPage =
                    auditLogRepository
                            .findAllByOrderByOccurredAtDesc(
                                    pageable
                            );
        } else {
            auditPage =
                    auditLogRepository
                            .searchAuditEvents(
                                    action,
                                    searchPattern,
                                    pageable
                            );
        }

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

    private String buildSearchPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String normalized = search.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.length() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search must be 100 characters or fewer"
            );
        }

        String escaped = normalized
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");

        return "%" + escaped + "%";
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