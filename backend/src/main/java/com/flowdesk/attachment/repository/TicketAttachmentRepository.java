package com.flowdesk.attachment.repository;

import com.flowdesk.attachment.domain.AttachmentStatus;
import com.flowdesk.attachment.domain.ScanStatus;
import com.flowdesk.attachment.domain.TicketAttachment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findByTicket_IdAndStatusOrderByCreatedAtAsc(
            UUID ticketId,
            AttachmentStatus status
    );

    List<TicketAttachment> findByTicket_IdAndStatusInOrderByCreatedAtAsc(
            UUID ticketId,
            Collection<AttachmentStatus> statuses
    );

    Optional<TicketAttachment> findByTicket_TicketNumberAndIdAndStatus(
            String ticketNumber,
            UUID id,
            AttachmentStatus status
    );

    Optional<TicketAttachment> findByTicket_TicketNumberAndId(
            String ticketNumber,
            UUID id
    );

    long countByTicket_IdAndStatusIn(
            UUID ticketId,
            Collection<AttachmentStatus> statuses
    );

    Optional<TicketAttachment> findByStorageKey(
            String storageKey
    );

    @Query(value = "SELECT * FROM ticket_attachments WHERE scan_status = 'PENDING_SCAN' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<TicketAttachment> findPendingForRescanWithSkipLocked(
            @Param("limit") int limit
    );

    List<TicketAttachment> findByScanStatusOrderByCreatedAtAsc(
            ScanStatus scanStatus,
            Pageable pageable
    );

    long countByScanStatus(
            ScanStatus scanStatus
    );
}
