package com.flowdesk.attachment.repository;

import com.flowdesk.attachment.domain.AttachmentStatus;
import com.flowdesk.attachment.domain.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
