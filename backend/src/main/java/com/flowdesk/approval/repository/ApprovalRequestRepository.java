package com.flowdesk.approval.repository;

import com.flowdesk.approval.domain.ApprovalRequest;
import com.flowdesk.approval.domain.ApprovalStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository
        extends JpaRepository<
                ApprovalRequest,
                UUID
        > {

    Optional<ApprovalRequest>
            findByTicket_Id(
                    UUID ticketId
            );

    boolean existsByTicket_Id(
            UUID ticketId
    );

    Page<ApprovalRequest>
            findByStatusOrderByRequestedAtAsc(
                    ApprovalStatus status,
                    Pageable pageable
            );

    @Lock(
            LockModeType.PESSIMISTIC_WRITE
    )
    @Query("""
            select approval
            from ApprovalRequest approval
            join fetch approval.ticket ticket
            join fetch approval.requestedByUser requester
            where approval.id = :approvalId
            """)
    Optional<ApprovalRequest>
            findByIdForUpdate(
                    @Param("approvalId")
                    UUID approvalId
            );
}