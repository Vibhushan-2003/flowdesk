package com.flowdesk.notification.repository;

import com.flowdesk.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = {
            "actorUser",
            "ticket"
    })
    Page<Notification> findByRecipientUser_IdOrderByCreatedAtDesc(
            UUID recipientUserId,
            Pageable pageable
    );

    long countByRecipientUser_IdAndReadAtIsNull(
            UUID recipientUserId
    );

    @EntityGraph(attributePaths = {
            "actorUser",
            "ticket"
    })
    Optional<Notification> findByIdAndRecipientUser_Id(
            UUID notificationId,
            UUID recipientUserId
    );
}